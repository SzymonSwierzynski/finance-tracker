# syntax=docker/dockerfile:1

# --- Build stage: install deps against the lockfile + produce the static bundle ---
# glibc (Debian slim), NOT alpine: on musl, Vite 8's Rolldown native binding falls back to a WASM
# runtime (@emnapi/*) that isn't recorded in package-lock.json, so strict `npm ci` fails. glibc
# loads the native binary directly and matches the CI runner, so the lockfile stays in sync.
FROM node:24-slim AS build
WORKDIR /app

# Warm the dependency layer before copying sources for better caching.
COPY package.json package-lock.json ./
RUN npm ci

# VITE_API_BASE_URL is intentionally left empty: the SPA calls /api on its own origin and nginx
# (runtime stage) proxies that to the backend. Same-origin keeps the refresh cookie first-party
# (SameSite=Lax), exactly like the Vite dev proxy — see CLAUDE.md §5 / HANDOFF §21 (3.5b #6).
COPY . .
RUN npm run build

# --- Runtime stage: nginx serving the bundle + reverse-proxying /api ---
FROM nginx:1.27-alpine AS runtime

# The image's entrypoint expands /etc/nginx/templates/*.template with envsubst at boot. The FILTER
# is load-bearing: without it envsubst would also blank nginx's own runtime vars ($host, $uri, …).
# Defaults target the compose network, so `docker compose up` needs no extra config; override the
# two FT_ vars to deploy on a platform with different internal DNS (see nginx.conf.template).
ENV NGINX_ENVSUBST_FILTER="^FT_" \
    FT_RESOLVER="127.0.0.11 ipv6=off valid=10s" \
    FT_API_UPSTREAM="backend"

COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
