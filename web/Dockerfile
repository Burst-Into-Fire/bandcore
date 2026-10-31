# syntax=docker/dockerfile:1

# ---- Build ----
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- Runtime: unprivileged nginx (listens on 8080, runs as uid 101) ----
FROM nginxinc/nginx-unprivileged:1.30-alpine

# Pick up Alpine security fixes released after the base image was built.
USER root
RUN apk upgrade --no-cache
USER 101

# Rendered by the image entrypoint with envsubst into /etc/nginx/conf.d/default.conf.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

# Where the API listens on the private network. The management port is never proxied.
ENV API_UPSTREAM=api:8080 \
    NGINX_ENVSUBST_FILTER=^API_

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/healthz > /dev/null || exit 1
