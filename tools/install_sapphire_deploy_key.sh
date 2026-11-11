#!/bin/sh
# Run once in the BaoTa Linux terminal. Adds only the Sapphire deployment key.
set -eu
if [ "$(id -u)" -ne 0 ]; then
    printf '%s\n' 'Please open the root terminal in BaoTa, then run this script again.'
    exit 1
fi
key='restrict ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKu+pTsz5G4UWTdlIDn2G+I9MZybOLFXB0gluPUCFOaX sapphire-deploy-20261031'
mkdir -p /root/.ssh
chmod 700 /root/.ssh
touch /root/.ssh/authorized_keys
if ! grep -qF 'AAAAC3NzaC1lZDI1NTE5AAAAIKu+pTsz5G4UWTdlIDn2G+I9MZybOLFXB0gluPUCFOaX' /root/.ssh/authorized_keys; then
    printf '\n%s\n' "$key" >> /root/.ssh/authorized_keys
fi
chmod 600 /root/.ssh/authorized_keys
printf '%s\n' 'SAPPHIRE_KEY_READY'
