# syntax=docker/dockerfile:1.7
# Imagen del frontend Angular servido por nginx. Contexto de construccion: la carpeta frontend.
#   docker build -t procesos-empresariales-frontend:local frontend
# nginx solo sirve archivos estaticos; las rutas /api/** las enruta el Ingress hacia el backend.

FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS compilacion
WORKDIR /app
COPY package.json package-lock.json ./
# Sin scripts de instalacion de terceros; el Angular CLI se ejecuta desde package-lock.json, sin npx.
RUN --mount=type=cache,target=/root/.npm npm ci --ignore-scripts --no-audit --no-fund
COPY angular.json tsconfig.json tsconfig.app.json tsconfig.spec.json ./
COPY public public
COPY src src
RUN npm test -- --watch=false \
    && npm run build -- --configuration production

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03 AS ejecucion
LABEL org.opencontainers.image.title="procesos-empresariales-frontend" \
      org.opencontainers.image.description="SPA Angular de Procesos Empresariales servida por nginx"
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=compilacion /app/dist/procesos-empresariales-frontend/browser/ /usr/share/nginx/aplicacion/
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
