#!/bin/bash
set -e

echo "Waiting for Kafka to be ready..."
sleep 10

echo "Creating Kafka topics..."

# Create order-events topic
kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --partitions 3 \
  --replication-factor 1 \
  --if-not-exists \
  --config retention.ms=604800000

echo "✓ Created order-events topic"

# Create payment-events topic
kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic payment-events \
  --partitions 3 \
  --replication-factor 1 \
  --if-not-exists \
  --config retention.ms=604800000

echo "✓ Created payment-events topic"

# Create dead-letter-queue topic
kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic dead-letter-queue \
  --partitions 1 \
  --replication-factor 1 \
  --if-not-exists \
  --config retention.ms=2592000000

echo "✓ Created dead-letter-queue topic"

echo "Listing all topics:"
kafka-topics --list --bootstrap-server localhost:9092

echo "Kafka topics initialization completed!"
