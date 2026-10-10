# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# SpendIO frontend - multi-stage build (ARCHITECTURE.md Phase 0 / Phase 16).
#   build stage : Node 22, production bundle
#   run  stage  : nginx serving the SPA, proxying /api to ${BACKEND_UPSTREAM}
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Same-origin API base so the nginx /api proxy is used in the served bundle.
ENV VITE_API_BASE_URL=/api/v1
RUN npm run build

FROM nginx:1.27-alpine AS runtime
# Rendered by the image entrypoint; override per environment.
ENV BACKEND_UPSTREAM=http://backend:8080
# The image entrypoint runs envsubst over /etc/nginx/templates/*.template.
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=4s --start-period=10s --retries=5 \
    CMD wget -qO- http://localhost/ >/dev/null || exit 1
