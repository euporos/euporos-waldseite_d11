#!/usr/bin/env bash
set -e

rsync -avz directus/uploads/ phylax@netcup-vps-2-arm:/home/phylax/projects/waldseite/app/directus/uploads/
