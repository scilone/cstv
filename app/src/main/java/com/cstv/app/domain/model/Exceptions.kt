package com.cstv.app.domain.model

class InvalidCredentialsException(message: String) : Exception(message)
class AccountExpiredException(message: String, val expiryDate: String) : Exception(message)
class ServerUnreachableException(message: String, cause: Throwable) : Exception(message, cause)
class NetworkTimeoutException(message: String, cause: Throwable) : Exception(message, cause)
class UnknownAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
