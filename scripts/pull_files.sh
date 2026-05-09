#!/usr/bin/env bash
set -e

rsync -avz phylax@netcup-vps-2-arm:/home/phylax/projects/waldseite/app/directus/uploads/ directus/uploads/
