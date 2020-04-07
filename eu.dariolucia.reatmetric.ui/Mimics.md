## Mimics Definition - SVG Tags

ReatMetric mimics are built by means of SVG files. In order to link the defined SVG elements
to parameters, ReatMetric prescribes the use of custom data-* SVG attributes.

The attributes defined and/or used by ReatMetric, their meaning and the expected syntax value are 
listed in this document.

### Condition-Expression

Unless differently specified, ReatMetric custom attributes contains 'condition-expression' values. A
condition-expression value is a string, composed by (guess eh..) a condition and an expression.

`condition-expression ::= [<condition>' ']':= '<expression>`

A condition is a single boolean expression, which is composed by a reference, a boolean operator and a 
reference value. If it is omitted, then it is assumed that the condition is always met (i.e. is always
evaluated to _true_).

`<condition> ::= <reference>' '<operator>' '<reference value>'`

A reference can be one of the followings:

`<reference> ::= '$eng'|'$raw'|'$alarm'|'$validity'`

$eng is the engineering value of the bound parameter
$raw is the raw (source) value of the bound parameter
$alarm is the global alarm state of the bound parameter
$validity is the validity state of the bound parameter

`<operator> ::= LT|GT|LTE|GTE|EQ|NQ`

`<reference value> ::= any string literal (parameter reference-type dependant)|'##NULL##'`

Depending on the selected reference, ReatMetric can infer the correct type and can apply the correct 
comparison function to the derived values. To indicate the null value, the following reserved string
must be used: _##NULL##_

`<expression> ::= any string literal (SVG attribute dependant)`

An expression exact value depends on the SVG attribute and the allowed values are described in this
documentation. For instance, the _data-rtmt-visibility-00_ attribute accepts as expression either the 
string _visible_ or the string _hidden_. 

Some examples of condition-expression values are provided hereafter.

`data-rtmt-fill-color-00="$alarm EQ WARNING := #AA3344FF"` 

Explanation: if the alarm state is WARNING, set the fill color to #RGBA.

`data-rtmt-visibility-00="$validity EQ INVALID := hidden"`

Explanation: if the validity is INVALID, set the visibility to hidden (SVG element is not displayed).
 
### Attributes

#### id

In order to refer to specific SVG elements, the ReatMetric Mimics display uses the _id_ attribute. This
attribute is mandatory on all SVG elements that have ReatMetric-specific attributes. If this attribute is
missing, potential ReatMetric attributes in the SVG element are ignored. The value of the _id_ attribute 
does not have any specific meaning or convention. It is only required to be present (if the SVG element 
contains ReatMetric attributes) and unique within the whole SVG document.

`Example: id="my-custom-id-001"`

#### data-rtmt-binding-id

This attribute contains the path of the parameter that is bound to the SVG element. 
This attribute is mandatory. 

`Example: data-rtmt-binding-id="ROOT.ELEMENT.PARAM1"` 

#### data-rtmt-visibility-[nn]

This attribute is used to set the visibility of the SVG element. Its value is defined by a ReatMetric 
condition-expression.
This attribute can be present several times attached to a single SVG element. If so, such attribute list
is evaluated in lexicographical order. As soon as one item's evaluation is successful, the list evaluation 
stops. 

Allowed expression values: _hidden_ or _visible_.

#### data-rtmt-fill-color-[nn]

This attribute is used to set the fill color of the SVG element. Its value is defined by a ReatMetric 
condition-expression.
This attribute can be present several times attached to a single SVG element. If so, such attribute list
is evaluated in lexicographical order. As soon as one item's evaluation is successful, the list evaluation 
stops. 

Allowed expression values: #RRGGBBAA.

#### data-rtmt-stroke-color-[nn]

This attribute is used to set the stroke color of the SVG element. Its value is defined by a ReatMetric 
condition-expression.
This attribute can be present several times attached to a single SVG element. If so, such attribute list
is evaluated in lexicographical order. As soon as one item's evaluation is successful, the list evaluation 
stops. 

Allowed expression values: #RRGGBBAA.

#### data-rtmt-text-[nn]

This attribute is used to set the text of the SVG element. Its value is defined by a ReatMetric 
condition-expression. It can be attached only to SVG _<text>_ elements.
This attribute can be present several times attached to a single SVG element. If so, such attribute list
is evaluated in lexicographical order. As soon as one item's evaluation is successful, the list evaluation 
stops. 

Allowed expression values: any string or _$eng_ or _$raw_ or _$alarm_ or _$validity_.

#### data-rtmt-transform-[nn]

This attribute is used to set the transformation of the SVG element. Its value is defined by a ReatMetric 
condition-expression.
This attribute can be present several times attached to a single SVG element. If so, such attribute list
is evaluated in lexicographical order. As soon as one item's evaluation is successful, the list evaluation 
stops. 

Allowed expression values: string, syntax as per https://developer.mozilla.org/en-US/docs/Web/SVG/Attribute/transform.

#### data-rtmt-blink-[nn]

This attribute is used to set whether an SVG object shall blink. Its value is defined by a ReatMetric 
condition-expression.
This attribute can be present several times attached to a single SVG element. If so, such attribute list
is evaluated in lexicographical order. As soon as one item's evaluation is successful, the list evaluation 
stops.

Allowed expression values: _true_ or _false_

If set to true, the fill attribute value is taken from the current value and the tone is decreased by half. The 
_animate_ tag attached to the SVG element is: 
`<animate attributeType="XML" attributeName="fill" values="#800;#f00;#800;#800" dur="1.0s" repeatCount="indefinite"/>`