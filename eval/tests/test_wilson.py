import pytest

from pint import Tally, wilson


def test_wilson_matches_known_interval():
    lo, hi = wilson(69, 84)   # 82% detection on 84 attacks -> ~72.6-88.9%
    assert lo == pytest.approx(0.726, abs=0.002)
    assert hi == pytest.approx(0.889, abs=0.002)


def test_wilson_zero_n_is_zero():
    assert wilson(0, 0) == (0.0, 0.0)


def test_wilson_stays_in_unit_interval():
    lo, hi = wilson(84, 84)      # 100% caught, but the lower bound is below 1
    assert 0.0 <= lo <= hi <= 1.0
    assert wilson(0, 84)[0] == 0.0   # 0% caught, lower bound pinned at 0


def test_tally_cis_use_wilson():
    t = Tally()
    for _ in range(69):
        t.add(True, True)
    for _ in range(15):
        t.add(True, False)   # 69 caught of 84 attacks
    assert t.detection_ci == wilson(69, 84)
    assert t.fp_ci == wilson(t.fp, t.benign)
