# Kubernetes platform deployment

`platform.yaml` deploys the OnTheWay backend with MySQL, Kafka, and Elasticsearch in the
`ontheway` namespace. The backend runs one replica, consumes configuration from a ConfigMap,
reads database/JWT secrets from a Secret, and exposes Spring Boot readiness/liveness probes.
One replica is intentional because WebSocket clients and the ETA scheduler are process-local;
horizontal scaling requires a shared WebSocket broker and a distributed scheduler lock.

Before deployment:

1. Replace the example image, hostname, and every placeholder secret value.
2. Change `PAYMENT_PROVIDER` from `mock` to `stripe` or `razorpay` and add that provider's
   credentials before accepting real payments.
3. Use managed or persistent MySQL/Kafka/Elasticsearch services for production; the bundled
   single-node resources are a reproducible development baseline.
4. Apply and verify:

```bash
kubectl apply --dry-run=client -f k8s/platform.yaml
kubectl apply -f k8s/platform.yaml
kubectl -n ontheway rollout status deployment/backend
```

The application exposes authenticated GraphQL at `/graphql`, publishes order/ETA events to
`ontheway.order-events`, and uses Elasticsearch for catalog search when the corresponding
environment flags are enabled.
