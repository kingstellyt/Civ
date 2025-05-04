---
title: Item Exchange
description: Item exchange plugin
---

# Item Exchange
An Item Exchange is a mechanic that allows users to trade items at predefined rates at a chest.

### Mechanics
Shop chests are created when they contain "Exchange Rules" (encoded buttons which specify the rulse of an exchange). Exchanges are made of pairs of inputs and outputs (though an output rule is not strictly necessary and a "donation" item exchange uses only an input rule). Bulk exchange rules are a single encoded button which contains within it one or more pairs of Exchange Rules. You can create a bulk exchange rule by placing an input and output button within a crafting grid. You can add bulk exchange buttons to other bulk exchange buttons to save space in your shop chest.

Players can view and cycle through a shop's available exchanges by punching a shop chest, preferably with an empty hand. If the player then punches the chest on a given exchange with a matching input, if the shop chest is stocked and has space available, then the exchange is transacted. 

Shops can, by default, be made with chests, trapped chests, barrels, dispensers, and droppers. You can check which containers are supported using the <code>/ieinfo shopblocks</code> command.

ItemExchange differs from other market and trade plugins in that it functions on a series of rules and criteria, rather than selling specific items. An Exchange Rule that ***ONLY*** specifies that the output is a Diamond Pickaxe will be able to output ***ANY*** Diamond Pickaxe, enchanted or not, used or not, repaired or not, named or not, etc. 

### Creating an Exchange Rule
There are three ways to create an Exchange Rule, each via the <code>/iecreate</code> command (<code>/iec</code>):
