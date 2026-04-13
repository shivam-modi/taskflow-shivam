package com.taskflow.common

class ResourceNotFoundException(message: String = "not found") : RuntimeException(message)
class ForbiddenException(message: String = "forbidden") : RuntimeException(message)
class ConflictException(message: String = "conflict") : RuntimeException(message)
class InvalidAssigneeException(message: String = "user not found") : RuntimeException(message)
