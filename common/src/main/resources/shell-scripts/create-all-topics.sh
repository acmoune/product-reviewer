cd /data/pkg/confluent/confluent-5.4.1

# 31556952000000 milliseconds == 1000 years

# Event sourcing topics
bin/kafka-topics --create --topic security-eventsource --partitions 1 --replication-factor 1 --config retention.ms=31556952000000 --zookeeper localhost:2181
bin/kafka-topics --create --topic reviews-eventsource --partitions 1 --replication-factor 1 --config retention.ms=31556952000000 --zookeeper localhost:2181

# Changelog topics
bin/kafka-topics --create --topic reviews-changelog --partitions 1 --replication-factor 1 --config cleanup.policy=compact --config delete.retention.ms=31556952000000 --zookeeper localhost:2181
bin/kafka-topics --create --topic stats-changelog --partitions 1 --replication-factor 1 --config cleanup.policy=compact --config delete.retention.ms=31556952000000 --zookeeper localhost:2181
bin/kafka-topics --create --topic tasks-changelog --partitions 1 --replication-factor 1 --config cleanup.policy=compact --config delete.retention.ms=31556952000000 --zookeeper localhost:2181
