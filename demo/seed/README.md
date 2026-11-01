# Seed

The worlds the nightly reset restores: `hub.tar.gz` and `lands.tar.gz`, made with `demo-reset.sh capture` (see
`../README.md`). Without them the reset starts both nodes on new worlds. `<database>.sql` files here are loaded into
that database after it is recreated. The data of Companies itself is not stored here: the `CompaniesDemo` plugin
creates it on `hub` after every reset.
