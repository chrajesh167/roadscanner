# syntax=docker/dockerfile:1

FROM nginx:1.27-alpine

# Replaces the default server block entirely rather than adding to conf.d — the stock config's
# catch-all would otherwise shadow the routes below depending on load order.
COPY nginx.conf /etc/nginx/nginx.conf

# Fails the build rather than shipping an image that will not start.
RUN nginx -t -c /etc/nginx/nginx.conf

EXPOSE 80

# 127.0.0.1, not localhost: nginx.conf declares `listen 80;` (IPv4), while busybox wget resolves
# localhost to [::1] first and is refused — marking a fully working ingress unhealthy.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
