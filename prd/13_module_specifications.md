# 13. Module Specifications

<!-- TOC -->
- [13. Module Specifications](#13-module-specifications)
  - [13.1. Built-in Modules (Phase 1)](#131-built-in-modules-phase-1)
    - [13.1.1. Database Modules](#1311-database-modules)
    - [13.1.2. Messaging Modules](#1312-messaging-modules)
  - [13.2. Module Interface Requirements](#132-module-interface-requirements)
<!-- /TOC -->

## 13.1. Built-in Modules (Phase 1)

### 13.1.1. Database Modules

1. **PostgreSQL**: Standard configuration, custom initialization scripts
2. **MySQL**: Version selection, custom config
3. **MongoDB**: Replica set support, authentication
4. **Redis**: Single instance, cluster mode

### 13.1.2. Messaging Modules

1. **Kafka**: Multi-broker, topic creation, schema registry
2. **RabbitMQ**: Queue/exchange pre-configuration, management UI access

## 13.2. Module Interface Requirements

- Sensible defaults for quick setup
- Full customization via fluent API
- Readiness check implementation
- Connection info exposure
- Proper cleanup
