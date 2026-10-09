"""Golden loan schedules, computed independently of the Java ScheduleCalculator.

Written from the definitions in ScheduleCalculator's documentation only (no code shared): Python's decimal module
at 34 significant digits, half-even rounding to 2 decimals. Run from this directory to regenerate golden.csv:

    python generate_golden.py

The Java test (ScheduleGoldenTest) must reproduce every installment of every case exactly.
"""
import calendar
import csv
from datetime import date, timedelta
from decimal import Decimal, ROUND_DOWN, ROUND_HALF_EVEN, getcontext

getcontext().prec = 34
CENT = Decimal("0.01")


def add_months(start, months):
    month_index = start.month - 1 + months
    year = start.year + month_index // 12
    month = month_index % 12 + 1
    day = min(start.day, calendar.monthrange(year, month)[1])
    return date(year, month, day)


def shift(start, frequency, k):
    if frequency == "DAILY":
        return start + timedelta(days=k)
    if frequency == "WEEKLY":
        return start + timedelta(weeks=k)
    if frequency == "BIWEEKLY":
        return start + timedelta(weeks=2 * k)
    if frequency == "MONTHLY":
        return add_months(start, k)
    if frequency == "QUARTERLY":
        return add_months(start, 3 * k)
    raise ValueError(frequency)


def year_fraction(start, end, day_count):
    if day_count == "ACTUAL_365F":
        return Decimal((end - start).days) / Decimal(365)
    if day_count == "ACTUAL_360":
        return Decimal((end - start).days) / Decimal(360)
    d1 = min(start.day, 30)
    d2 = 30 if end.day == 31 and d1 >= 30 else end.day
    days = 360 * (end.year - start.year) + 30 * (end.month - start.month) + (d2 - d1)
    return Decimal(days) / Decimal(360)


def schedule(case):
    principal = Decimal(case["principal"])
    n = case["installments"]
    disbursed = case["disbursement"]
    first = case["first_due"] or shift(disbursed, case["frequency"], 1)
    principal_grace = case["principal_grace"]
    interest_grace = case["interest_grace"]
    annual = Decimal(case["rate"]) / Decimal(100)

    dues = [shift(first, case["frequency"], k) for k in range(n)]
    starts = [disbursed] + dues[:-1]
    rates = [annual * year_fraction(starts[k], dues[k], case["day_count"]) for k in range(n)]

    amortising = n - principal_grace
    equal_principal = (principal / Decimal(amortising)).quantize(CENT, rounding=ROUND_DOWN)
    installment = None
    if case["method"] == "DECLINING_BALANCE_EQUAL_INSTALLMENT":
        discount = Decimal(1)
        total = Decimal(0)
        for k in range(principal_grace, n):
            discount = discount / (Decimal(1) + rates[k])
            total += discount
        installment = (principal / total).quantize(CENT, rounding=ROUND_HALF_EVEN)

    rows = []
    outstanding = principal
    exact = Decimal(0)
    charged = Decimal(0)
    for k in range(n):
        base = principal if case["method"] == "FLAT" else outstanding
        exact += base * rates[k]
        interest = Decimal("0.00")
        if k >= interest_grace:
            rounded = exact.quantize(CENT, rounding=ROUND_HALF_EVEN)
            interest = rounded - charged
            charged = rounded
        if k < principal_grace:
            paid = Decimal("0.00")
        elif k == n - 1:
            paid = outstanding
        elif installment is not None:
            paid = min(max(installment - interest, Decimal(0)), outstanding)
        else:
            paid = min(equal_principal, outstanding)
        outstanding -= paid
        rows.append((k + 1, starts[k], dues[k], paid, interest, paid + interest, outstanding))
    return rows


def cases():
    methods = ["FLAT", "DECLINING_BALANCE_EQUAL_INSTALLMENT", "DECLINING_BALANCE_EQUAL_PRINCIPAL"]
    frequencies = {"DAILY": 30, "WEEKLY": 16, "BIWEEKLY": 13, "MONTHLY": 12, "QUARTERLY": 8}
    day_counts = ["ACTUAL_365F", "ACTUAL_360", "THIRTY_360"]
    number = 0
    for method in methods:
        for frequency, installments in frequencies.items():
            for day_count in day_counts:
                number += 1
                yield dict(case=f"C{number:03d}", method=method, frequency=frequency, day_count=day_count,
                           principal="5000.00", rate="28.5", installments=installments,
                           disbursement=date(2027, 1, 31), first_due=None, principal_grace=0, interest_grace=0)
    special = [
        dict(method="DECLINING_BALANCE_EQUAL_INSTALLMENT", frequency="MONTHLY", day_count="THIRTY_360",
             principal="10000.00", rate="12", installments=12, disbursement=date(2027, 1, 15)),
        dict(method="FLAT", frequency="MONTHLY", day_count="THIRTY_360", principal="1000.00", rate="24",
             installments=6, disbursement=date(2027, 1, 15)),
        dict(method="DECLINING_BALANCE_EQUAL_PRINCIPAL", frequency="WEEKLY", day_count="ACTUAL_365F",
             principal="777.77", rate="36", installments=10, disbursement=date(2027, 3, 3),
             first_due=date(2027, 3, 13)),
        dict(method="DECLINING_BALANCE_EQUAL_INSTALLMENT", frequency="MONTHLY", day_count="ACTUAL_365F",
             principal="25000.00", rate="18", installments=24, disbursement=date(2027, 2, 10), principal_grace=3,
             interest_grace=1),
        dict(method="FLAT", frequency="BIWEEKLY", day_count="ACTUAL_360", principal="3000.00", rate="30",
             installments=12, disbursement=date(2027, 6, 1), principal_grace=2, interest_grace=2),
        dict(method="DECLINING_BALANCE_EQUAL_INSTALLMENT", frequency="WEEKLY", day_count="ACTUAL_365F",
             principal="1500.00", rate="0", installments=7, disbursement=date(2027, 4, 1)),
        dict(method="DECLINING_BALANCE_EQUAL_PRINCIPAL", frequency="QUARTERLY", day_count="THIRTY_360",
             principal="0.05", rate="12", installments=4, disbursement=date(2027, 5, 31)),
        dict(method="FLAT", frequency="MONTHLY", day_count="ACTUAL_365F", principal="999.99", rate="17.25",
             installments=1, disbursement=date(2027, 12, 31)),
        dict(method="DECLINING_BALANCE_EQUAL_INSTALLMENT", frequency="MONTHLY", day_count="ACTUAL_360",
             principal="123456.78", rate="9.999999", installments=60, disbursement=date(2027, 1, 29),
             first_due=date(2027, 3, 1)),
    ]
    for extra in special:
        number += 1
        yield dict(dict(case=f"C{number:03d}", first_due=None, principal_grace=0, interest_grace=0), **extra)


def main():
    with open("golden.csv", "w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["case", "method", "frequency", "dayCount", "principal", "rate", "installments",
                         "disbursement", "firstDue", "principalGrace", "interestGrace", "number", "from", "due",
                         "principalDue", "interestDue", "totalDue", "outstandingAfter"])
        for case in cases():
            for row in schedule(case):
                writer.writerow([case["case"], case["method"], case["frequency"], case["day_count"],
                                 case["principal"], case["rate"], case["installments"],
                                 case["disbursement"].isoformat(),
                                 case["first_due"].isoformat() if case["first_due"] else "",
                                 case["principal_grace"], case["interest_grace"], row[0], row[1].isoformat(),
                                 row[2].isoformat(), f"{row[3]:.2f}", f"{row[4]:.2f}", f"{row[5]:.2f}",
                                 f"{row[6]:.2f}"])


if __name__ == "__main__":
    main()
