# Initial Administrator

On first startup with a completely empty `users` table, the application creates:

- Username: `admin`
- Password: `admin123456`
- Role: `ADMIN`

Change this public default password before opening a new installation to users.
Use `PASSWORD_PLAIN=false` in production so the configured password encoder uses BCrypt.

If any user already exists, startup does not create, promote, or modify accounts.
Upgrading an existing installation therefore preserves its administrator password.
The legacy manual `data.sql` seed is not required for administrator creation.
