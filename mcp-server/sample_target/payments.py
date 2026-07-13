def charge_card(token, amount):
    _validate_token(token)
    return _submit_to_gateway(token, amount)


def _validate_token(token):
    if not token:
        raise ValueError("invalid token")


def _submit_to_gateway(token, amount):
    return {"charged": amount}
