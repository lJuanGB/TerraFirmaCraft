### Changes

- Update `zh_tw` (#2817), `ko_kr` (#2826) localization

### Fixes

- Fix crash caused in edge case by snow/ice removal during world generation (#2860)
- Fix explosions caused by gunpowder bowls not consuming the gunpowder (#2856)
- Fix horses not getting overburdened by more than one heavy/huge item (#2854)
- Fix crash when setting the config option `enableLeavesDecaySlowly` to true (#2848)

### Technical Changes

- Tag `#tfc:seeds` as `#forge:seeds` (#2851)
- Fix advanced shaped recipes validation for input row/column preventing some possible valid recipes. 