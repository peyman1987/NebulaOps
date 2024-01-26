FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go/cache-service/go.mod go/cache-service/go.sum* ./
RUN go mod download
COPY go/cache-service/ ./
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/cache-service ./cmd/server

FROM alpine:3.20
RUN adduser -D appuser
USER appuser
COPY --from=build /out/cache-service /cache-service
EXPOSE 8091
ENTRYPOINT ["/cache-service"]
