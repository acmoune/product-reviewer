package common.util

object Validator {
  def checkEmail(email: String): Boolean = {
    val emailRegex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

    email match {
      case null                                                   => false
      case email if email.trim.isEmpty                            => false
      case email if emailRegex.findFirstMatchIn(email).isDefined  => true
      case _                                                      => false
    }
  }
}
