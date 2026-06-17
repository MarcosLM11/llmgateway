# llmgateway

Un gateway OpenAI-compatible que cualquier cliente del SDK de OpenAI puede usar cambiando solo la base_url.
Arquitectura con inversión de dependencias: el dominio define los contratos, los adaptadores los implementan.
Frontera doble bien dibujada: DTO externo ↔ dominio ↔ DTO externo, en ambos extremos (entrada HTTP y salida hacia proveedor).
Modularización verificada por test: las dependencias entre módulos están explícitas y no se pueden romper accidentalmente.
Configuración externalizada con @ConfigurationProperties.
Spring Boot 4.1 + Java 26 + Jackson 3 sobre tools.jackson.
"API keys stored as plaintext for demo purposes; production should hash with Argon2id"
API keys plaintext, sin timing-safe compare, principal como String (futuro: clase tipada).