def send_receipt(cart_id):
    email = _lookup_email(cart_id)
    _send_email(email, "receipt")


def _lookup_email(cart_id):
    return "user@example.com"


def _send_email(to, template):
    return True
