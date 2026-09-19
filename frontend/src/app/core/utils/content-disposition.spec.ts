import { fileNameFromContentDisposition } from './content-disposition';

describe('fileNameFromContentDisposition', () => {
  it('reads a quoted plain file name', () => {
    expect(fileNameFromContentDisposition('attachment; filename="Login.feature"')).toBe('Login.feature');
  });

  it('prefers the UTF-8 name and decodes it', () => {
    expect(fileNameFromContentDisposition(
      "attachment; filename=\"=?UTF-8?Q?Anmeldung=C3=A4.feature?=\"; filename*=UTF-8''Anmeldung%C3%A4.feature",
    )).toBe('Anmeldungä.feature');
  });

  it('is null without a header or a file name', () => {
    expect(fileNameFromContentDisposition(null)).toBeNull();
    expect(fileNameFromContentDisposition('attachment')).toBeNull();
  });
});
