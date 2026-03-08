rootProject.name = "rca-agent"

include(
    ":services:order-service",
    ":services:payment-service",
    ":services:inventory-service",
    ":services:notification-service",
    ":agent"
)
