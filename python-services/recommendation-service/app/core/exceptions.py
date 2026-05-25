class RecommendationServiceError(Exception):
    """Base domain exception for recommendation service."""


class ExternalDependencyError(RecommendationServiceError):
    """Raised when external dependency access fails."""
