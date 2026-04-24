FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go/event-worker/go.mod go/event-worker/go.sum* ./
RUN go mod download
COPY go/event-worker/ ./
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/event-worker ./cmd/worker

FROM alpine:3.20
RUN adduser -D appuser
USER appuser
COPY --from=build /out/event-worker /event-worker
ENTRYPOINT ["/event-worker"]
