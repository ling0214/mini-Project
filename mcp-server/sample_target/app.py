from payments import charge_card
from notifications import send_receipt


def checkout_endpoint(cart_id, payment_token):
    total = calculate_total(cart_id)
    charge_card(payment_token, total)
    send_receipt(cart_id)
    return {"status": "ok", "total": total}


def calculate_total(cart_id):
    items = get_cart_items(cart_id)
    return sum(item["price"] for item in items)


def get_cart_items(cart_id):
    return _db_fetch_cart(cart_id)


def _db_fetch_cart(cart_id):
    return []
