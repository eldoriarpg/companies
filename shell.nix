{ pkgs ? import <nixpkgs> {}, ... }:

let
  playwrightPkgs = import (fetchTarball {
    url = "https://github.com/NixOS/nixpkgs/archive/c59305bab2065cfecc4944690d9eedbb56f3a9fa.tar.gz";
    sha256 = "16rsfnnxk6294sz6asx0shblirkhm4yyvkimq3v00c0y2114gp7b";
  }) {};
in
pkgs.mkShell {
  packages = with pkgs; [jdk25 nodejs_24];

  PLAYWRIGHT_BROWSERS_PATH = "${playwrightPkgs.playwright-driver.browsers}";
  PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "1";
}
