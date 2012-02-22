# Migration notes

## View layer

Everything related to Themes is now available via `themeInfo`

- `viewUtils.themeProperty('someKey')` --> `themeInfo.get('someKey')`
- `viewUtils.getKey('i18n.key')` --> `messages.get('i18n.key')`
- `views.context.package.themePath('path')` --> `themeInfo.path('path')`
- `views.context.package.themeDisplayName` --> `themeInfo.displayName`

All helper functionality, previously mostly in `views.context.package` is now in `views.Helpers`

- `views.context.package.PAGE_SIZE()` --> `views.Helpers.PAGE_SIZE()`
- `views.context.package.niceText('text')` --> `views.Helpers.niceText('text')`
- `views.context.package.niceTime(time)` --> `views.Helpers.niceText(time)`


## Pull requests to already update from

https://github.com/delving/culture-hub/pull/431/files#diff-2