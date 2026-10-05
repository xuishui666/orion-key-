# Initial Administrator

On first startup with a completely empty `users` table, set these values in the
API container environment before starting it:

- `ADMIN_BOOTSTRAP_PASSWORD`: a unique, strong password; leave it out of Git and shell history.
- `PASSWORD_PLAIN=false`: required so the password is stored with BCrypt.

The application then creates:

- Username: `admin`
- Password: the value of `ADMIN_BOOTSTRAP_PASSWORD`
- Role: `ADMIN`

The storefront does not offer customer registration or sign-in. Open `/login`
directly to sign in as administrator; account details and password changes
remain available under Profile in the admin sidebar.

Without the password, or with plaintext password storage enabled, a new
installation fails startup instead of creating an insecure account. The
repository `docker-compose.yml` passes `.env` to the API container via
`env_file`. With a custom Compose file, explicitly pass both variables to
the API service. After the first successful startup, remove the bootstrap
password from the runtime environment and restart the API.

If any user already exists, startup does not create, promote, or modify accounts.
Upgrading an existing installation therefore preserves its administrator password.
`data.sql` only contains non-account seed data and does not create administrators.
