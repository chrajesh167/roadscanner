# syntax=docker/dockerfile:1

# --- Build stage --------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Lockfile first so the dependency layer is reused whenever only source changed.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# NEXT_PUBLIC_* is BUILD-time configuration: Next.js inlines these values into the client bundle,
# so they cannot be changed by restarting the container — a different public origin means a new
# image. They are declared here so a production build can supply the real origin:
#
#   docker build --build-arg NEXT_PUBLIC_AUTH_API_URL=https://<origin> ... .
#
# The defaults are the same localhost ports src/lib/api/config.ts already falls back to, matching
# .env.example. They are deliberately NOT empty strings: an empty value would satisfy the `??`
# fallback in config.ts and produce broken relative URLs instead of the intended default.
ARG NEXT_PUBLIC_AUTH_API_URL=http://localhost:8081
ARG NEXT_PUBLIC_SEARCH_API_URL=http://localhost:8082
ARG NEXT_PUBLIC_INVENTORY_API_URL=http://localhost:8084
ARG NEXT_PUBLIC_BOOKING_API_URL=http://localhost:8085
ARG NEXT_PUBLIC_PAYMENT_API_URL=http://localhost:8086

ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# --- Runtime stage -------------------------------------------------------
FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

RUN addgroup -S roadscanner && adduser -S customerweb -G roadscanner

# Only the standalone output: server.js plus the traced subset of node_modules. No source, no
# lockfile, no dev dependencies. `static` is copied separately because Next deliberately leaves it
# out of the traced bundle — without it every asset 404s.
COPY --from=build --chown=customerweb:roadscanner /app/.next/standalone ./
COPY --from=build --chown=customerweb:roadscanner /app/.next/static ./.next/static

USER customerweb:roadscanner

EXPOSE 3000

# 127.0.0.1 rather than localhost: the standalone server binds IPv4 only, while busybox wget
# resolves localhost to [::1] and would be refused, failing a container that is serving correctly.
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -qO- http://127.0.0.1:3000/ >/dev/null 2>&1 || exit 1

CMD ["node", "server.js"]
