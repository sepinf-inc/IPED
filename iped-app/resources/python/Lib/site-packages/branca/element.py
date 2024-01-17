"""
Element
-------

A generic class for creating Elements.

"""

import base64
import json
import warnings
from binascii import hexlify
from collections import OrderedDict
from html import escape
from os import urandom
from pathlib import Path
from urllib.request import urlopen

from jinja2 import Environment, PackageLoader, Template

from .utilities import _camelify, _parse_size, none_max, none_min

ENV = Environment(loader=PackageLoader("branca", "templates"))


class Element:
    """Basic Element object that does nothing.
    Other Elements may inherit from this one.

    Parameters
    ----------
    template : str, default None
        A jinaj2-compatible template string for rendering the element.
        If None, template will be:

        .. code-block:: jinja

            {% for name, element in this._children.items() %}
            {{element.render(**kwargs)}}
            {% endfor %}

        so that all the element's children are rendered.
    template_name : str, default None
        If no template is provided, you can also provide a filename.

    """

    _template = Template(
        "{% for name, element in this._children.items() %}\n"
        "    {{element.render(**kwargs)}}"
        "{% endfor %}",
    )

    def __init__(self, template=None, template_name=None):
        self._name = "Element"
        self._id = hexlify(urandom(16)).decode()
        self._env = ENV
        self._children = OrderedDict()
        self._parent = None
        self._template_str = template
        self._template_name = template_name

        if template is not None:
            self._template = Template(template)
        elif template_name is not None:
            self._template = ENV.get_template(template_name)

    def __getstate__(self):
        """Modify object state when pickling the object.
        jinja2 Environment cannot be pickled, so set
        the ._env attribute to None. This will be added back
        when unpickling (see __setstate__)
        """
        state: dict = self.__dict__.copy()
        state["_env"] = None
        state.pop("_template", None)
        return state

    def __setstate__(self, state: dict):
        """Re-add ._env attribute when unpickling"""
        state["_env"] = ENV

        if state["_template_str"] is not None:
            state["_template"] = Template(state["_template_str"])
        elif state["_template_name"] is not None:
            state["_template"] = ENV.get_template(state["_template_name"])

        self.__dict__.update(state)

    def get_name(self):
        """Returns a string representation of the object.
        This string has to be unique and to be a python and
        javascript-compatible
        variable name.
        """
        return _camelify(self._name) + "_" + self._id

    def _get_self_bounds(self):
        """Computes the bounds of the object itself (not including it's children)
        in the form [[lat_min, lon_min], [lat_max, lon_max]]
        """
        return [[None, None], [None, None]]

    def get_bounds(self):
        """Computes the bounds of the object and all it's children
        in the form [[lat_min, lon_min], [lat_max, lon_max]].
        """
        bounds = self._get_self_bounds()

        for child in self._children.values():
            child_bounds = child.get_bounds()
            bounds = [
                [
                    none_min(bounds[0][0], child_bounds[0][0]),
                    none_min(bounds[0][1], child_bounds[0][1]),
                ],
                [
                    none_max(bounds[1][0], child_bounds[1][0]),
                    none_max(bounds[1][1], child_bounds[1][1]),
                ],
            ]
        return bounds

    def add_children(self, child, name=None, index=None):
        """Add a child."""
        warnings.warn(
            "Method `add_children` is deprecated. Please use `add_child` instead.",
            FutureWarning,
            stacklevel=2,
        )
        return self.add_child(child, name=name, index=index)

    def add_child(self, child, name=None, index=None):
        """Add a child."""
        if name is None:
            name = child.get_name()
        if index is None:
            self._children[name] = child
        else:
            items = [item for item in self._children.items() if item[0] != name]
            items.insert(int(index), (name, child))
            self._children = OrderedDict(items)
        child._parent = self
        return self

    def add_to(self, parent, name=None, index=None):
        """Add element to a parent."""
        parent.add_child(self, name=name, index=index)
        return self

    def to_dict(self, depth=-1, ordered=True, **kwargs):
        """Returns a dict representation of the object."""
        if ordered:
            dict_fun = OrderedDict
        else:
            dict_fun = dict
        out = dict_fun()
        out["name"] = self._name
        out["id"] = self._id
        if depth != 0:
            out["children"] = dict_fun(
                [
                    (name, child.to_dict(depth=depth - 1))
                    for name, child in self._children.items()
                ],
            )  # noqa
        return out

    def to_json(self, depth=-1, **kwargs):
        """Returns a JSON representation of the object."""
        return json.dumps(self.to_dict(depth=depth, ordered=True), **kwargs)

    def get_root(self):
        """Returns the root of the elements tree."""
        if self._parent is None:
            return self
        else:
            return self._parent.get_root()

    def render(self, **kwargs):
        """Renders the HTML representation of the element."""
        return self._template.render(this=self, kwargs=kwargs)

    def save(self, outfile, close_file=True, **kwargs):
        """Saves an Element into a file.

        Parameters
        ----------
        outfile : str or file object
            The file (or filename) where you want to output the html.
        close_file : bool, default True
            Whether the file has to be closed after write.
        """
        if isinstance(outfile, (str, bytes, Path)):
            fid = open(outfile, "wb")
        else:
            fid = outfile

        root = self.get_root()
        html = root.render(**kwargs)
        fid.write(html.encode("utf8"))
        if close_file:
            fid.close()


class Link(Element):
    """An abstract class for embedding a link in the HTML."""

    def get_code(self):
        """Opens the link and returns the response's content."""
        if self.code is None:
            self.code = urlopen(self.url).read()
        return self.code

    def to_dict(self, depth=-1, **kwargs):
        """Returns a dict representation of the object."""
        out = super().to_dict(depth=-1, **kwargs)
        out["url"] = self.url
        return out


class JavascriptLink(Link):
    """Create a JavascriptLink object based on a url.

    Parameters
    ----------
    url : str
        The url to be linked
    download : bool, default False
        Whether the target document shall be loaded right now.

    """

    _template = Template(
        '{% if kwargs.get("embedded",False) %}'
        "<script>{{this.get_code()}}</script>"
        "{% else %}"
        '<script src="{{this.url}}"></script>'
        "{% endif %}",
    )

    def __init__(self, url, download=False):
        super().__init__()
        self._name = "JavascriptLink"
        self.url = url
        self.code = None
        if download:
            self.get_code()


class CssLink(Link):
    """Create a CssLink object based on a url.

    Parameters
    ----------
    url : str
        The url to be linked
    download : bool, default False
        Whether the target document shall be loaded right now.

    """

    _template = Template(
        '{% if kwargs.get("embedded",False) %}'
        "<style>{{this.get_code()}}</style>"
        "{% else %}"
        '<link rel="stylesheet" href="{{this.url}}"/>'
        "{% endif %}",
    )

    def __init__(self, url, download=False):
        super().__init__()
        self._name = "CssLink"
        self.url = url
        self.code = None
        if download:
            self.get_code()


class Figure(Element):
    """Create a Figure object, to plot things into it.

    Parameters
    ----------
    width : str, default "100%"
        The width of the Figure.
        It may be a percentage or pixel value (like "300px").
    height : str, default None
        The height of the Figure.
        It may be a percentage or a pixel value (like "300px").
    ratio : str, default "60%"
        A percentage defining the aspect ratio of the Figure.
        It will be ignored if height is not None.
    title : str, default None
        Figure title.
    figsize : tuple of two int, default None
        If you're a matplotlib addict, you can overwrite width and
        height. Values will be converted into pixels in using 60 dpi.
        For example figsize=(10, 5) will result in
        width="600px", height="300px".
    """

    _template = Template(
        "<!DOCTYPE html>\n"
        "<html>\n"
        "<head>\n"
        "{% if this.title %}<title>{{this.title}}</title>{% endif %}"
        "    {{this.header.render(**kwargs)}}\n"
        "</head>\n"
        "<body>\n"
        "    {{this.html.render(**kwargs)}}\n"
        "</body>\n"
        "<script>\n"
        "    {{this.script.render(**kwargs)}}\n"
        "</script>\n"
        "</html>\n",
    )

    def __init__(
        self,
        width="100%",
        height=None,
        ratio="60%",
        title=None,
        figsize=None,
    ):
        super().__init__()
        self._name = "Figure"
        self.header = Element()
        self.html = Element()
        self.script = Element()

        self.header._parent = self
        self.html._parent = self
        self.script._parent = self

        self.width = width
        self.height = height
        self.ratio = ratio
        self.title = title
        if figsize is not None:
            self.width = str(60 * figsize[0]) + "px"
            self.height = str(60 * figsize[1]) + "px"

        # Create the meta tag.
        self.header.add_child(
            Element(
                '<meta http-equiv="content-type" content="text/html; charset=UTF-8" />',
            ),  # noqa
            name="meta_http",
        )

    def to_dict(self, depth=-1, **kwargs):
        """Returns a dict representation of the object."""
        out = super().to_dict(depth=depth, **kwargs)
        out["header"] = self.header.to_dict(depth=depth - 1, **kwargs)
        out["html"] = self.html.to_dict(depth=depth - 1, **kwargs)
        out["script"] = self.script.to_dict(depth=depth - 1, **kwargs)
        return out

    def get_root(self):
        """Returns the root of the elements tree."""
        return self

    def render(self, **kwargs):
        """Renders the HTML representation of the element."""
        for name, child in self._children.items():
            child.render(**kwargs)
        return self._template.render(this=self, kwargs=kwargs)

    def _repr_html_(self, **kwargs):
        """Displays the Figure in a Jupyter notebook."""
        html = escape(self.render(**kwargs))
        if self.height is None:
            iframe = (
                '<div style="width:{width};">'
                '<div style="position:relative;width:100%;height:0;padding-bottom:{ratio};">'  # noqa
                '<span style="color:#565656">Make this Notebook Trusted to load map: File -> Trust Notebook</span>'  # noqa
                '<iframe srcdoc="{html}" style="position:absolute;width:100%;height:100%;left:0;top:0;'  # noqa
                'border:none !important;" '
                "allowfullscreen webkitallowfullscreen mozallowfullscreen>"
                "</iframe>"
                "</div></div>"
            ).format(html=html, width=self.width, ratio=self.ratio)
        else:
            iframe = (
                '<iframe srcdoc="{html}" width="{width}" height="{height}"'
                'style="border:none !important;" '
                '"allowfullscreen" "webkitallowfullscreen" "mozallowfullscreen">'
                "</iframe>"
            ).format(html=html, width=self.width, height=self.height)
        return iframe

    def add_subplot(self, x, y, n, margin=0.05):
        """Creates a div child subplot in a matplotlib.figure.add_subplot style.

        Parameters
        ----------
        x : int
            The number of rows in the grid.
        y : int
            The number of columns in the grid.
        n : int
            The cell number in the grid, counted from 1 to x*y.

        Example:
        >>> fig.add_subplot(3, 2, 5)
        # Create a div in the 5th cell of a 3rows x 2columns
        grid(bottom-left corner).
        """
        width = 1.0 / y
        height = 1.0 / x
        left = ((n - 1) % y) * width
        top = ((n - 1) // y) * height

        left = left + width * margin
        top = top + height * margin
        width = width * (1 - 2.0 * margin)
        height = height * (1 - 2.0 * margin)

        div = Div(
            position="absolute",
            width=f"{100.0 * width}%",
            height=f"{100.0 * height}%",
            left=f"{100.0 * left}%",
            top=f"{100.0 * top}%",
        )
        self.add_child(div)
        return div


class Html(Element):
    """Create an HTML div object for embedding data.

    Parameters
    ----------
    data : str
        The HTML data to be embedded.
    script : bool
        If True, data will be embedded without escaping
        (suitable for embedding html-ready code)
    width : int or str, default '100%'
        The width of the output div element.
        Ex: 120 , '80%'
    height : int or str, default '100%'
        The height of the output div element.
        Ex: 120 , '80%'
    """

    _template = Template(
        '<div id="{{this.get_name()}}" '
        'style="width: {{this.width[0]}}{{this.width[1]}}; height: {{this.height[0]}}{{this.height[1]}};">'  # noqa
        "{% if this.script %}{{this.data}}{% else %}{{this.data|e}}{% endif %}</div>",
    )  # noqa

    def __init__(self, data, script=False, width="100%", height="100%"):
        super().__init__()
        self._name = "Html"
        self.script = script
        self.data = data

        self.width = _parse_size(width)
        self.height = _parse_size(height)


class Div(Figure):
    """Create a Div to be embedded in a Figure.

    Parameters
    ----------
    width: int or str, default '100%'
        The width of the div in pixels (int) or percentage (str).
    height: int or str, default '100%'
        The height of the div in pixels (int) or percentage (str).
    left: int or str, default '0%'
        The left-position of the div in pixels (int) or percentage (str).
    top: int or str, default '0%'
        The top-position of the div in pixels (int) or percentage (str).
    position: str, default 'relative'
        The position policy of the div.
        Usual values are 'relative', 'absolute', 'fixed', 'static'.
    """

    _template = Template(
        "{% macro header(this, kwargs) %}"
        "<style> #{{this.get_name()}} {\n"
        "        position : {{this.position}};\n"
        "        width : {{this.width[0]}}{{this.width[1]}};\n"
        "        height: {{this.height[0]}}{{this.height[1]}};\n"
        "        left: {{this.left[0]}}{{this.left[1]}};\n"
        "        top: {{this.top[0]}}{{this.top[1]}};\n"
        "    </style>"
        "{% endmacro %}"
        "{% macro html(this, kwargs) %}"
        '<div id="{{this.get_name()}}">{{this.html.render(**kwargs)}}</div>'
        "{% endmacro %}",
    )

    def __init__(
        self,
        width="100%",
        height="100%",
        left="0%",
        top="0%",
        position="relative",
    ):
        super(Figure, self).__init__()
        self._name = "Div"

        # Size Parameters.
        self.width = _parse_size(width)
        self.height = _parse_size(height)
        self.left = _parse_size(left)
        self.top = _parse_size(top)
        self.position = position

        self.header = Element()
        self.html = Element(
            "{% for name, element in this._children.items() %}"
            "{{element.render(**kwargs)}}"
            "{% endfor %}",
        )
        self.script = Element()

        self.header._parent = self
        self.html._parent = self
        self.script._parent = self

    def get_root(self):
        """Returns the root of the elements tree."""
        return self

    def render(self, **kwargs):
        """Renders the HTML representation of the element."""
        figure = self._parent
        assert isinstance(figure, Figure), (
            "You cannot render this Element " "if it is not in a Figure."
        )

        for name, element in self._children.items():
            element.render(**kwargs)

        for name, element in self.header._children.items():
            figure.header.add_child(element, name=name)

        for name, element in self.script._children.items():
            figure.script.add_child(element, name=name)

        header = self._template.module.__dict__.get("header", None)
        if header is not None:
            figure.header.add_child(Element(header(self, kwargs)), name=self.get_name())

        html = self._template.module.__dict__.get("html", None)
        if html is not None:
            figure.html.add_child(Element(html(self, kwargs)), name=self.get_name())

        script = self._template.module.__dict__.get("script", None)
        if script is not None:
            figure.script.add_child(Element(script(self, kwargs)), name=self.get_name())

    def _repr_html_(self, **kwargs):
        """Displays the Div in a Jupyter notebook."""
        if self._parent is None:
            self.add_to(Figure())
            out = self._parent._repr_html_(**kwargs)
            self._parent = None
        else:
            out = self._parent._repr_html_(**kwargs)
        return out


class IFrame(Element):
    """Create a Figure object, to plot things into it.

    Parameters
    ----------
    html : str, default None
        Eventual HTML code that you want to put in the frame.
    width : str, default "100%"
        The width of the Figure.
        It may be a percentage or pixel value (like "300px").
    height : str, default None
        The height of the Figure.
        It may be a percentage or a pixel value (like "300px").
    ratio : str, default "60%"
        A percentage defining the aspect ratio of the Figure.
        It will be ignored if height is not None.
    figsize : tuple of two int, default None
        If you're a matplotlib addict, you can overwrite width and
        height. Values will be converted into pixels in using 60 dpi.
        For example figsize=(10, 5) will result in
        width="600px", height="300px".
    """

    def __init__(self, html=None, width="100%", height=None, ratio="60%", figsize=None):
        super().__init__()
        self._name = "IFrame"

        self.width = width
        self.height = height
        self.ratio = ratio
        if figsize is not None:
            self.width = str(60 * figsize[0]) + "px"
            self.height = str(60 * figsize[1]) + "px"

        if isinstance(html, str) or isinstance(html, bytes):
            self.add_child(Element(html))
        elif html is not None:
            self.add_child(html)

    def render(self, **kwargs):
        """Renders the HTML representation of the element."""
        html = super().render(**kwargs)
        html = "data:text/html;charset=utf-8;base64," + base64.b64encode(
            html.encode("utf8"),
        ).decode(
            "utf8",
        )  # noqa

        if self.height is None:
            iframe = (
                '<div style="width:{width};">'
                '<div style="position:relative;width:100%;height:0;padding-bottom:{ratio};">'  # noqa
                '<iframe src="{html}" style="position:absolute;width:100%;height:100%;left:0;top:0;'  # noqa
                'border:none !important;">'
                "</iframe>"
                "</div></div>"
            ).format(html=html, width=self.width, ratio=self.ratio)
        else:
            iframe = (
                '<iframe src="{html}" width="{width}" style="border:none !important;" '
                'height="{height}"></iframe>'
            ).format(html=html, width=self.width, height=self.height)
        return iframe


class MacroElement(Element):
    """This is a parent class for Elements defined by a macro template.
    To compute your own element, all you have to do is:

    * To inherit from this class
    * Overwrite the '_name' attribute
    * Overwrite the '_template' attribute with something of the form::

        {% macro header(this, kwargs) %}
            ...
        {% endmacro %}

        {% macro html(this, kwargs) %}
            ...
        {% endmacro %}

        {% macro script(this, kwargs) %}
            ...
        {% endmacro %}

    """

    _template = Template("")

    def __init__(self):
        super().__init__()
        self._name = "MacroElement"

    def render(self, **kwargs):
        """Renders the HTML representation of the element."""
        figure = self.get_root()
        assert isinstance(figure, Figure), (
            "You cannot render this Element " "if it is not in a Figure."
        )

        header = self._template.module.__dict__.get("header", None)
        if header is not None:
            figure.header.add_child(Element(header(self, kwargs)), name=self.get_name())

        html = self._template.module.__dict__.get("html", None)
        if html is not None:
            figure.html.add_child(Element(html(self, kwargs)), name=self.get_name())

        script = self._template.module.__dict__.get("script", None)
        if script is not None:
            figure.script.add_child(Element(script(self, kwargs)), name=self.get_name())

        for name, element in self._children.items():
            element.render(**kwargs)
