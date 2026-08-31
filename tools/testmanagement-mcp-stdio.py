#!/usr/bin/env python3
"""
stdio -> HTTP bridge for the Testmanagement MCP server (PRD-028).

Some MCP clients register stdio servers only: you give them a command to spawn and they talk
JSON-RPC over that process's stdin and stdout. This server speaks streamable-HTTP. Point such a
client at an HTTPS URL and it registers nothing -- no error, no tools, silently -- and the model
then has no tools to call.

This is the adapter. Read a JSON-RPC message from stdin, POST it to /api/mcp, write the reply to
stdout. That is the whole program, and it should stay that way: it adds no behaviour, so every
tool, schema and error a client sees is whatever the server said.

It is this short because the server is STATELESS (PRD-025 3.1) -- no session to carry, no
reconnect, no long-poll to hold open.

Usage, from an MCP client config:

    {"mcpServers": {"testmanagement": {
      "command": "python3",
      "args": ["/path/to/testmanagement-mcp-stdio.py"],
      "env": {"TESTMANAGEMENT_URL": "https://your-instance/api/mcp",
              "TESTMANAGEMENT_API_KEY": "tm_..."}}}}

Requires Python 3.9+. Standard library only, deliberately: it has to be copyable into an
air-gapped environment with no pip, no npm and no network route except to the instance.

===========================================================================================
NOTHING MAY BE WRITTEN TO STDOUT EXCEPT A JSON-RPC MESSAGE.

stdout is the protocol channel. A stray print(), a traceback, a warning -- anything at all --
corrupts the stream, and the client's only symptom is that it silently stops talking. Diagnostics
go to stderr. There is a test asserting this; if you are adding output, that is where it goes.
===========================================================================================
"""

import json
import os
import signal
import sys
import urllib.error
import urllib.request

# Both types are required. The server answers a bare "Accept: application/json" with an empty 400,
# which is a confusing way to find that out.
ACCEPT = "application/json, text/event-stream"

# Not the default. urllib sends "Python-urllib/3.x", which Cloudflare's managed rules block
# outright -- an instance behind it answers 403 with error 1010, "banned based on your browser's
# signature", before the request ever reaches the application. That is indistinguishable from a
# rejected key unless you look at the body, and it cost an afternoon to find the first time.
# Identifying honestly is also better for anyone reading their access log.
DEFAULT_USER_AGENT = "testmanagement-mcp-stdio/1.0"

DEFAULT_TIMEOUT_SECONDS = 120

URL_VAR = "TESTMANAGEMENT_URL"
KEY_VAR = "TESTMANAGEMENT_API_KEY"
TIMEOUT_VAR = "TESTMANAGEMENT_TIMEOUT_SECONDS"
USER_AGENT_VAR = "TESTMANAGEMENT_USER_AGENT"


def log(message):
    """Diagnostics. stderr, always -- see the banner above."""
    print(message, file=sys.stderr, flush=True)


def error_response(request_id, code, message):
    return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}


def recover_id(raw):
    """
    The id of a malformed message, if it can be salvaged.

    A client waiting on a request it can no longer match to a reply hangs, so it is worth some
    effort to answer even a message we could not otherwise parse.
    """
    try:
        parsed = json.loads(raw)
    except (ValueError, TypeError):
        return None
    if isinstance(parsed, dict):
        return parsed.get("id")
    return None


def one_line(body):
    """
    The response as exactly one line.

    MCP's stdio transport is newline-delimited JSON: a message is a line, and must not contain
    embedded newlines. The server sends compact JSON, so the fast path is to forward the bytes
    untouched rather than re-serialising them -- which would risk altering number formatting in
    a payload we are only supposed to be carrying.
    """
    if b"\n" not in body and b"\r" not in body:
        return body
    return json.dumps(json.loads(body.decode("utf-8")), separators=(",", ":")).encode("utf-8")


def write(body):
    """One message, one line, flushed. Binary, so Windows does not translate \\n into \\r\\n."""
    sys.stdout.buffer.write(one_line(body))
    sys.stdout.buffer.write(b"\n")
    sys.stdout.buffer.flush()


def forward(url, key, timeout, raw, user_agent=DEFAULT_USER_AGENT):
    """
    POST one message and return the response body, or None when there is nothing to say.

    Returns None for a notification: a JSON-RPC message with no id gets a 202 and an empty body,
    and the client is not waiting for anything. Writing an empty line there would be a protocol
    error of our own invention.
    """
    request = urllib.request.Request(
        url,
        data=raw,
        method="POST",
        headers={
            "Authorization": "Bearer " + key,
            "Content-Type": "application/json",
            "Accept": ACCEPT,
            "User-Agent": user_agent,
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        body = response.read()
    return body if body.strip() else None


def handle(url, key, timeout, raw, user_agent=DEFAULT_USER_AGENT):
    """One message in, zero or one messages out. Never raises."""
    try:
        return forward(url, key, timeout, raw, user_agent)

    except urllib.error.HTTPError as exc:
        request_id = recover_id(raw)
        detail = ""
        try:
            detail = exc.read().decode("utf-8", "replace")[:400]
        except Exception:  # noqa: BLE001 - reading the error body is best effort
            pass

        if exc.code == 401:
            message = (
                "The Testmanagement instance rejected the API key (HTTP 401). Check %s -- the key "
                "must start with tm_ and must not be revoked." % KEY_VAR
            )
        elif exc.code == 403:
            # Deliberately not "bad key": the application answers 401 for that. A 403 on this path
            # more often comes from something in front of it -- a CDN or WAF blocking the client
            # signature, which is what Cloudflare's error 1010 is -- and telling someone to check
            # their key sends them to look at the one thing that is fine.
            message = (
                "HTTP 403 from %s. The application answers 401 for a bad key, so a 403 usually "
                "comes from a proxy or WAF in front of it rather than from Testmanagement. If the "
                "response below mentions Cloudflare or a browser signature, set %s to something "
                "the proxy allows. Response: %s" % (url, USER_AGENT_VAR, detail or "(empty)")
            )
        elif exc.code == 404:
            message = (
                "No MCP endpoint at %s (HTTP 404). Either the URL is wrong or MCP is switched off "
                "on the instance (MCP_ENABLED=true)." % url
            )
        else:
            message = "Testmanagement returned HTTP %d. %s" % (exc.code, detail)

        log("error: " + message)
        # A notification that failed has no id and nobody waiting; say so on stderr and move on.
        if request_id is None:
            return None
        return json.dumps(error_response(request_id, -32603, message)).encode("utf-8")

    except Exception as exc:  # noqa: BLE001 - a hang is worse than any error we can report
        request_id = recover_id(raw)
        message = "Could not reach the Testmanagement instance at %s: %s" % (url, exc)
        log("error: " + message)
        if request_id is None:
            return None
        return json.dumps(error_response(request_id, -32603, message)).encode("utf-8")


def configuration():
    """
    URL and key from the environment.

    Not from argv: arguments are visible in `ps` to every user on the machine and land in shell
    history, and this one is a credential (PRD-019). A key passed as an argument is refused rather
    than quietly accepted.
    """
    url = os.environ.get(URL_VAR, "").strip()
    key = os.environ.get(KEY_VAR, "").strip()

    if not url or not key:
        log(
            "testmanagement-mcp-stdio: both %s and %s must be set in the environment.\n"
            "  %s   e.g. https://your-instance.example.com/api/mcp\n"
            "  %s   the tm_... key from Settings -> API Keys\n"
            "Pass them via the \"env\" block of your MCP client config, not as arguments -- an "
            "API key on the command line is visible to every process on the machine."
            % (URL_VAR, KEY_VAR, URL_VAR, KEY_VAR)
        )
        return None

    if any(argument.startswith("tm_") for argument in sys.argv[1:]):
        log(
            "testmanagement-mcp-stdio: refusing to take an API key from the command line. "
            "Set %s in the environment instead." % KEY_VAR
        )
        return None

    try:
        timeout = int(os.environ.get(TIMEOUT_VAR, DEFAULT_TIMEOUT_SECONDS))
    except ValueError:
        timeout = DEFAULT_TIMEOUT_SECONDS
        log("warning: %s is not a number, using %ds" % (TIMEOUT_VAR, DEFAULT_TIMEOUT_SECONDS))

    user_agent = os.environ.get(USER_AGENT_VAR, "").strip() or DEFAULT_USER_AGENT

    return url, key, timeout, user_agent


def main():
    # Exit quietly. Some clients surface a traceback on stderr as a crashed server.
    for received in (signal.SIGINT, signal.SIGTERM):
        signal.signal(received, lambda *_: sys.exit(0))

    config = configuration()
    if config is None:
        return 2
    url, key, timeout, user_agent = config

    log("testmanagement-mcp-stdio: bridging stdio to %s" % url)

    # Binary, line by line: text mode on Windows would translate newlines and corrupt the framing.
    for line in sys.stdin.buffer:
        raw = line.strip()
        if not raw:
            continue
        response = handle(url, key, timeout, raw, user_agent)
        if response is not None:
            write(response)

    # stdin closed: the client is shutting down.
    return 0


if __name__ == "__main__":
    sys.exit(main())
