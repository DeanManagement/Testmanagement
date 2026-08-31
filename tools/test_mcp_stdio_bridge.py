#!/usr/bin/env python3
"""
Tests for testmanagement-mcp-stdio.py (PRD-028 §5).

    python3 -m unittest discover -s tools -v

unittest rather than pytest, and a stub server from http.server rather than a mocking library, so
this runs anywhere the bridge itself runs: no pip, no network. The bridge's whole promise is that
it needs nothing installed, and a test suite that needed something installed would be testing a
different program.

The bridge is run as a subprocess in most of these, because the properties that matter are about
its stdin, stdout and stderr, and asserting on those means having real ones.
"""

import json
import os
import subprocess
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

BRIDGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "testmanagement-mcp-stdio.py")


class StubHandler(BaseHTTPRequestHandler):
    """Stands in for /api/mcp. Records what it was sent so the headers can be asserted."""

    def do_POST(self):  # noqa: N802 - name fixed by BaseHTTPRequestHandler
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        self.server.requests.append({"headers": dict(self.headers), "body": body})

        status, payload = self.server.responder(body)
        self.send_response(status)
        if payload:
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        else:
            self.send_header("Content-Length", "0")
            self.end_headers()

    def log_message(self, *args):
        pass  # the stub's own logging would confuse the test output


def echo_result(body):
    """
    Default stub behaviour: 200 with a result for a request, 202 and nothing for a notification.

    Tolerates a body it cannot parse, because one test deliberately sends one — and a stub that
    raised there would print a traceback into the test output for a passing test, which is its own
    small way of training people to ignore stack traces in CI.
    """
    try:
        message = json.loads(body.decode("utf-8"))
    except ValueError:
        return 400, b'{"error":"stub could not parse that"}'
    if not isinstance(message, dict) or message.get("id") is None:
        return 202, b""
    payload = json.dumps({"jsonrpc": "2.0", "id": message["id"], "result": {"ok": True}})
    return 200, payload.encode("utf-8")


class BridgeTestCase(unittest.TestCase):

    def setUp(self):
        self.server = HTTPServer(("127.0.0.1", 0), StubHandler)
        self.server.requests = []
        self.server.responder = echo_result
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = "http://127.0.0.1:%d/api/mcp" % self.server.server_address[1]

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def run_bridge(self, stdin_text, env=None, url=None, key="tm_testkey", argv=()):
        environment = dict(os.environ)
        environment.pop("TESTMANAGEMENT_URL", None)
        environment.pop("TESTMANAGEMENT_API_KEY", None)
        if url is not False:
            environment["TESTMANAGEMENT_URL"] = url or self.url
        if key is not False:
            environment["TESTMANAGEMENT_API_KEY"] = key
        environment.update(env or {})

        completed = subprocess.run(
            [sys.executable, BRIDGE, *argv],
            input=stdin_text.encode("utf-8"),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=environment,
            timeout=30,
        )
        return completed

    # --- framing -------------------------------------------------------------------------

    def test_one_request_produces_exactly_one_line_of_valid_json(self):
        result = self.run_bridge('{"jsonrpc":"2.0","id":7,"method":"tools/list","params":{}}\n')

        lines = result.stdout.decode("utf-8").splitlines()
        self.assertEqual(len(lines), 1, "one message in, one line out")
        self.assertEqual(json.loads(lines[0])["id"], 7, "the id must survive the round trip")

    def test_several_requests_stay_one_per_line_and_in_order(self):
        stdin = "".join(
            '{"jsonrpc":"2.0","id":%d,"method":"tools/list","params":{}}\n' % i for i in range(5)
        )
        result = self.run_bridge(stdin)

        ids = [json.loads(line)["id"] for line in result.stdout.decode("utf-8").splitlines()]
        self.assertEqual(ids, [0, 1, 2, 3, 4])

    def test_a_multiline_response_is_flattened_to_one_line(self):
        # A server that pretty-printed would otherwise break the one-message-per-line contract.
        def pretty(body):
            message = json.loads(body.decode("utf-8"))
            payload = json.dumps({"jsonrpc": "2.0", "id": message["id"], "result": {"ok": True}},
                                 indent=2)
            return 200, payload.encode("utf-8")

        self.server.responder = pretty
        result = self.run_bridge('{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n')

        lines = result.stdout.decode("utf-8").splitlines()
        self.assertEqual(len(lines), 1)
        self.assertTrue(json.loads(lines[0])["result"]["ok"])

    def test_blank_lines_on_stdin_are_ignored(self):
        result = self.run_bridge('\n\n{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n\n')

        self.assertEqual(len(result.stdout.decode("utf-8").splitlines()), 1)

    # --- notifications -------------------------------------------------------------------

    def test_a_notification_produces_no_output_at_all(self):
        """
        Asserted on byte count, not on parsing: "no reply" and "an empty reply" look identical
        until something downstream tries to read one, and only one of them is correct.
        """
        result = self.run_bridge('{"jsonrpc":"2.0","method":"notifications/initialized"}\n')

        self.assertEqual(result.stdout, b"", "a notification must produce zero bytes on stdout")
        self.assertEqual(len(self.server.requests), 1, "but it must still be forwarded")

    def test_a_notification_that_fails_still_produces_no_output(self):
        self.server.responder = lambda body: (500, b'{"error":"boom"}')
        result = self.run_bridge('{"jsonrpc":"2.0","method":"notifications/initialized"}\n')

        self.assertEqual(result.stdout, b"", "nobody is waiting on a notification")
        self.assertIn(b"error", result.stderr.lower())

    # --- stdout purity -------------------------------------------------------------------

    def test_stdout_carries_nothing_but_json_even_when_there_are_diagnostics(self):
        """
        The test that catches a stray print() a year from now.

        stdout is the protocol channel; anything else on it corrupts the stream and the client's
        only symptom is that it silently stops talking. This run deliberately produces diagnostics
        (a failing request logs to stderr) and asserts they did not leak.
        """
        self.server.responder = lambda body: (500, b"upstream exploded")
        stdin = ('{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n'
                 '{"jsonrpc":"2.0","method":"notifications/initialized"}\n')

        result = self.run_bridge(stdin)

        self.assertTrue(result.stderr, "the failure should have been reported somewhere")
        for line in result.stdout.decode("utf-8").splitlines():
            json.loads(line)  # raises if anything non-JSON reached stdout

    def test_the_startup_banner_goes_to_stderr(self):
        result = self.run_bridge('{"jsonrpc":"2.0","method":"notifications/initialized"}\n')

        self.assertEqual(result.stdout, b"")
        self.assertIn(b"bridging stdio", result.stderr)

    # --- headers -------------------------------------------------------------------------

    def test_both_accept_types_are_sent_on_every_request(self):
        """
        The real server answers a bare "Accept: application/json" with an empty 400. Asserting it
        against a stub is the only way to find out here rather than in someone's client.
        """
        self.run_bridge('{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n')

        accept = self.server.requests[0]["headers"]["Accept"]
        self.assertIn("application/json", accept)
        self.assertIn("text/event-stream", accept)

    def test_the_key_is_sent_as_a_bearer_token(self):
        self.run_bridge('{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n', key="tm_abc123")

        self.assertEqual(self.server.requests[0]["headers"]["Authorization"], "Bearer tm_abc123")

    def test_it_does_not_identify_as_python_urllib(self):
        """
        Found against a live instance, not in a test. urllib's default User-Agent is
        "Python-urllib/3.x", which Cloudflare's managed rules block outright: the instance answers
        403 with error 1010 before the request reaches the application, and it looks exactly like a
        rejected key. Any instance behind a CDN would have found the bridge simply broken.
        """
        self.run_bridge('{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n')

        user_agent = self.server.requests[0]["headers"]["User-Agent"]
        self.assertNotIn("Python-urllib", user_agent)
        self.assertIn("testmanagement", user_agent)

    def test_the_user_agent_can_be_overridden_for_a_stricter_proxy(self):
        self.run_bridge('{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n',
                        env={"TESTMANAGEMENT_USER_AGENT": "curl/8.5.0"})

        self.assertEqual(self.server.requests[0]["headers"]["User-Agent"], "curl/8.5.0")

    def test_a_403_points_at_the_proxy_rather_than_the_key(self):
        """The application answers 401 for a bad key, so blaming the key on a 403 misdirects."""
        self.server.responder = lambda body: (403, b'{"title":"Error 1010: Access denied"}')
        result = self.run_bridge('{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n')

        message = json.loads(result.stdout.decode("utf-8").splitlines()[0])["error"]["message"]
        self.assertIn("proxy", message.lower())
        self.assertIn("TESTMANAGEMENT_USER_AGENT", message)
        self.assertIn("1010", message, "the upstream body is what identifies the real cause")

    def test_the_body_is_forwarded_verbatim(self):
        # The bridge is a pipe. It must not reformat, re-key or "helpfully" fix the message.
        message = '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"x","a":[1,2]}}'
        self.run_bridge(message + "\n")

        self.assertEqual(self.server.requests[0]["body"], message.encode("utf-8"))

    # --- failure modes -------------------------------------------------------------------

    def test_an_unreachable_instance_answers_rather_than_hangs(self):
        """A request that never gets a reply hangs the client forever with no indication why."""
        result = self.run_bridge('{"jsonrpc":"2.0","id":3,"method":"tools/list"}\n',
                                 url="http://127.0.0.1:1/api/mcp")

        reply = json.loads(result.stdout.decode("utf-8").splitlines()[0])
        self.assertEqual(reply["id"], 3)
        self.assertIn("error", reply)

    def test_a_rejected_key_names_the_credential(self):
        self.server.responder = lambda body: (401, b"")
        result = self.run_bridge('{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n')

        message = json.loads(result.stdout.decode("utf-8").splitlines()[0])["error"]["message"]
        self.assertIn("API key", message)
        self.assertIn("TESTMANAGEMENT_API_KEY", message)

    def test_a_404_suggests_the_feature_switch(self):
        # MCP_ENABLED defaults to false, so 404 is the likeliest first-run failure.
        self.server.responder = lambda body: (404, b"")
        result = self.run_bridge('{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n')

        message = json.loads(result.stdout.decode("utf-8").splitlines()[0])["error"]["message"]
        self.assertIn("MCP_ENABLED", message)

    def test_a_malformed_line_still_gets_an_answer_when_an_id_can_be_recovered(self):
        self.server.responder = lambda body: (400, b"not json-rpc")
        result = self.run_bridge('{"id":42,"nonsense":true}\n')

        reply = json.loads(result.stdout.decode("utf-8").splitlines()[0])
        self.assertEqual(reply["id"], 42)

    def test_an_unparseable_line_produces_no_stdout_and_a_diagnostic(self):
        result = self.run_bridge("this is not json at all\n")

        self.assertEqual(result.stdout, b"")
        self.assertTrue(result.stderr)

    # --- configuration -------------------------------------------------------------------

    def test_missing_environment_refuses_to_start_and_names_both_variables(self):
        result = self.run_bridge("", url=False, key=False)

        self.assertEqual(result.returncode, 2)
        self.assertIn(b"TESTMANAGEMENT_URL", result.stderr)
        self.assertIn(b"TESTMANAGEMENT_API_KEY", result.stderr)
        self.assertEqual(result.stdout, b"")

    def test_a_key_passed_as_an_argument_is_refused(self):
        """
        argv is visible in `ps` to every user on the machine and lands in shell history. Accepting
        it quietly would make the insecure path the convenient one.
        """
        result = self.run_bridge("", argv=("tm_secretkey",))

        self.assertEqual(result.returncode, 2)
        self.assertIn(b"command line", result.stderr)
        self.assertNotIn(b"tm_secretkey", result.stdout)

    def test_closing_stdin_exits_cleanly(self):
        result = self.run_bridge("")

        self.assertEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
