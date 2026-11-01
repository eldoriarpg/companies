---
title: Treasury
sidebar_position: 5
---

# Treasury

Every company has a treasury: a shared account for the whole company. Members pay into it, and the company can keep a
small share of every payout there. Use it to save for a rename, to reward your members, or as a reserve.

## Open the treasury

The treasury of your company shows on the [dashboard](../players/dashboard.md)'s portfolio and company page: the
balance, the payout share and the latest movements, with a chart over time. Every member can **Pay in**; members with
the `treasury` permission can **Take out** money and set the share of payouts it keeps.

In game, `/company bank` shows the same, with `/company bank deposit <amount>`, `withdraw <amount>`,
`share <percent>` and `log [page]`. In the dialog you type the amount into a field and set the share with a slider;
in chat, the buttons put the command into your chat line.

Amounts have at most two decimals. The smallest deposit or withdrawal is 1 by default. You can only withdraw what the
treasury holds.

:::note
If your server turned the treasury off, the treasury and the bank commands are missing. Balances are kept, and the
treasury comes back when the server turns it on again.
:::

## The payout share

When an order is done, every member who delivered gets paid. The payout share is the part of each payout that goes
into the treasury instead.

The share is taken after tax. Say a member earns 100, the tax is 10 % and the share is 20 %:

| Step | Amount |
|---|---|
| Payout for the delivered items | 100 |
| Tax (10 %) | 10 |
| After tax | 90 |
| Into the treasury (20 % of 90) | 18 |
| The member gets | 72 |

The treasury part is rounded down to whole cents. The member keeps the rest. The payment message shows each member
what went to the treasury.

Set the share on the dashboard's company page or with `/company bank share <percent>`. It can be 0 to the highest share
your server allows, 25 % by default. Every member gets a message when the share changes. If the server lowers its
highest share, your company keeps at most the new highest share from then on.

## The log

`/company bank log [page]` lists the transactions of the treasury, newest first. Each one has a kind:

| Kind | What it is |
|---|---|
| Deposit | A member paid in |
| Withdrawal | A member took money out |
| Payout share | The share of a payout |
| Rename | The owner paid a rename from the treasury |
| Refund | Money that came back after a payment failed, for example a withdrawal the economy refused |
| Disband payout | The balance was paid to the owner when the company was disbanded |
| Added by an admin / Removed by an admin | An admin changed the balance |
| Share changed | The payout share was changed. No money moved. |
| Contest prize | The company's prize of a contest |
| Home teleport | A teleport home paid from the treasury |
| Area bought / Area rent / Area sold back | A [company area](./areas.md) bought or rented from the server, or sold back to it |

Log entries are kept for 365 days by default.

## Pay a rename from the treasury

When the owner renames the company and the treasury holds enough, the rename offers to pay from the treasury.
See [Profile](./profile.md#rename-the-company).

## When the company is disbanded

The whole balance is paid to the owner, who gets a message. See
[Members and permissions](./members-and-permissions.md#disband-the-company).
