from payments import charge_card


def test_charge_card_returns_charged_amount():
    result = charge_card("tok_test", 42)
    assert result["charged"] == 42
