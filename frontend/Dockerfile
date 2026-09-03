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

# Where the SPA sends API calls. Vite inlines this at BUILD time, so it must be an ARG — a runtime
# env var arrives too late and the bundle ships with an empty base URL.
#
# Empty (the default) = same-origin: the SPA calls /api and nginx proxies it, which keeps the
# refresh cookie first-party (SameSite=Lax). That's the preferred setup and is what Docker Compose
# uses. On Railway private DNS doesn't resolve, so this is set to the backend's PUBLIC domain,
# making the API cross-origin — which then requires SameSite=None; Secure on the cookie and the
# frontend origin in APP_CORS_ALLOWED_ORIGINS. See nginx.conf.template.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

COPY . .
RUN npm run build

# --- Runtime stage: nginx serving the bundle + reverse-proxying /api ---
# Debian (glibc), NOT alpine: Railway's private network (*.railway.internal) is IPv6-only and
# musl's resolver cannot resolve it — the backend, on glibc, reaches postgres.railway.internal
# fine while an alpine nginx got NXDOMAIN for backend.railway.internal from every resolver,
# including the system one. ENABLE_ALPINE_PRIVATE_NETWORKING=true did not help. Costs ~40MB.
FROM nginx:1.27 AS runtime

# The image's entrypoint expands /etc/nginx/templates/*.template with envsubst at boot. The FILTER
# is load-bearing: without it envsubst would also blank nginx's own runtime vars ($host, $uri, …).
# Defaults target the compose network, so `docker compose up` needs no extra config; override the
# two FT_ vars to deploy on a platform with different internal DNS (see nginx.conf.template).
# Only FT_ vars are templated. The filter is load-bearing: without it envsubst would also blank
# nginx's own runtime vars ($host, $uri, …). Keep it free of '$' — Dockerfile ENV does its own
# expansion, so a regex anchor here gets mangled before nginx sees it. The listen port is NOT
# templated (see nginx.conf.template); set the platform's target port to 80 instead.
ENV NGINX_ENVSUBST_FILTER="^FT_"
ENV FT_API_UPSTREAM="backend"

COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
