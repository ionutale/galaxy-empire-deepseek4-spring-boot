# Builds any one of the Go services. Select with --build-arg SERVICE=<name>,
# where <name> is a directory under cmd/ (eureka-server, config-server,
# gateway, auth-service, game-service).
FROM golang:1.26-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
ARG SERVICE
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/app ./cmd/${SERVICE}

FROM alpine:3.20
RUN apk add --no-cache wget ca-certificates && adduser -S -u 1001 appuser
USER appuser
COPY --from=build /out/app /app
ENTRYPOINT ["/app"]
