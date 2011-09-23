# Validation

The data entered by users is validated in two ways: once directly at the client-side, and once when the data is submitted, at the server level.

The validation rules are defined at the view model level, using the [Play validation annotations](http://www.playframework.org/documentation/1.2.3/validation#annotations).
In order for the validation annotations to work in Play-Scala you need to import the Scala wrappers:

     import play.data.validation.Annotations._


## Server-side validation

In the `submit` handler method, after the object has been deserialized, the server-side validation is called:

    validate(objectModel).foreach { errors => return JsonBadRequest(objectModel.copy(errors = errors)) }

Validation errors are collected into a Map that contains as key the name of the field that caused the error. For global errors, `global` is used as a key.
There is only one error returned at once per field. Anyway, server-side errors should be rare, they should be caught on the client-side before even submitting the request.

In the view, fields need to bind an error span - this is done already for template fields such as `textField`s etc. where it looks like this:

    <span class="error" data-bind="text: errors().@name">@showError(name)</span>

The global error message is displayed on the top of the page:

    <div data-bind="text: errors().global"></div>