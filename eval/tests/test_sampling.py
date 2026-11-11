import random

from datasets_loader import Record, _reservoir, split_for


def _rows(n):
    return (Record(f"r{i}", f"text {i}", "benign", "t") for i in range(n))


def test_reservoir_is_deterministic_for_a_seed():
    a = _reservoir(_rows(10_000), 50, random.Random("42-t"))
    b = _reservoir(_rows(10_000), 50, random.Random("42-t"))
    assert [r.id for r in a] == [r.id for r in b]


def test_reservoir_is_not_the_first_k():
    picked = _reservoir(_rows(10_000), 50, random.Random("42-t"))
    assert [r.id for r in picked] != [f"r{i}" for i in range(50)]


def test_split_is_stable_and_ignores_case_and_spacing():
    assert split_for("Ignore  previous") == split_for("ignore previous")


def test_split_is_roughly_30_70():
    dev = sum(split_for(f"prompt {i}") == "dev" for i in range(10_000))
    assert 2_700 < dev < 3_300
