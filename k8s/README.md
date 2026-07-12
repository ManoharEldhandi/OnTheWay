# Kubernetes platform deployment

`platform.yaml` deploys the OnTheWay backend with MySQL, Kafka, and Elasticsearch in the
`ontheway` namespace. The backend runs two replicas, consumes configuration from a ConfigMap,
reads database/JWT secrets from a Secret, and exposes Spring Boot readiness/liveness probes.

Before deployment:

1. Replace the example image and secret values.
2. Use managed or persistent MySQL/Kafka/Elasticsearch services for production; the bundled
   single-node resources are a reproducible development baseline.
3. Apply and verify:

```bash
kubectl apply --dry-run=client -f k8s/platform.yaml
kubectl apply -f k8s/platform.yaml
kubectl -n ontheway rollout status deployment/backend
```

The application exposes authenticated GraphQL at `/graphql`, publishes order/ETA events to
`ontheway.order-events`, and uses Elasticsearch for catalog search when the corresponding
environment flags are enabled.
