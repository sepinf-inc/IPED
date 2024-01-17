# Copyright (c) Jean-Charles Lefebvre
# SPDX-License-Identifier: MIT

import datetime

from . import profile
from .utils import scrub_method_name

__all__ = [
    'FIT_UTC_REFERENCE', 'FIT_DATETIME_MIN',
    'DataProcessorBase', 'DefaultDataProcessor', 'StandardUnitsDataProcessor']


#: Datetimes (uint32) represent seconds since this ``FIT_UTC_REFERENCE``
#: (unix timestamp for UTC 00:00 Dec 31 1989).
FIT_UTC_REFERENCE = 631065600

#: ``date_time`` typed fields for which value is below ``FIT_DATETIME_MIN``
#: represent the number of seconds elapsed since device power on.
FIT_DATETIME_MIN = 0x10000000


class DataProcessorBase:
    """
    Data processing base class.

    This class does nothing. It is meant to be derived.

    The following methods are called by :class:`fitdecode.FitReader`:

    * `on_header`, each time a :class:`fitdecode.FitHeader` is reached
    * `on_crc`, each time a :class:`fitdecode.FitCRC` (the FIT footer) is
      reached

    This is convenient if you wish to reset some context-sensitive state in-
    between two chained FIT files for example.

    Bear in mind that a malformed/corrupted file may miss either of these
    entities (header and/or CRC footer).

    Also, the following methods are called (still by
    :class:`fitdecode.FitReader`) for each field of every data message, in that
    order:

    * `on_process_type`
    * `on_process_field`
    * `on_process_unit`
    * `on_process_message`

    By default, the above processor methods call the following methods if they
    exist (hence the aforementioned caching)::

        def process_type_<type_name>(reader, field_data)
        def process_field_<field_name>(reader, field_data)  # could be unknown_XYZ but NOT recommended  # noqa
        def process_units_<unit_name>(reader, field_data)
        def process_message_<mesg_name>(reader, data_message)

    ``process_*`` methods are not expected to return any value and may alter
    the content of the passed *field_data* (:class:`fitdecode.FieldData`) and
    *data_message* (:class:`fitdecode.FitDataMessage`) arguments if needed.

    .. seealso:: `DefaultDataProcessor`, `StandardUnitsDataProcessor`
    """

    def __init__(self):
        self._method_cache = {}

    def on_header(self, reader, fit_header):
        pass

    def on_crc(self, reader, fit_crc):
        pass

    def on_process_type(self, reader, field_data):
        self._run_processor(
            f'process_type_{field_data.type.name}',
            reader, field_data)

    def on_process_field(self, reader, field_data):
        if field_data.name:
            self._run_processor(
                f'process_field_{field_data.name}',
                reader, field_data)

    def on_process_unit(self, reader, field_data):
        if field_data.units:
            self._run_processor(
                f'process_units_{field_data.units}',
                reader, field_data)

    def on_process_message(self, reader, data_message):
        self._run_processor(
            f'process_message_{data_message.def_mesg.name}',
            reader, data_message)

    def _run_processor(self, method_name, reader, data):
        method = self._resolve_method(method_name)
        if method is not None:
            method(reader, data)

    def _resolve_method(self, method_name):
        method = self._method_cache.get(method_name, False)
        if method is not False:
            return method

        scrubbed_method_name = scrub_method_name(method_name)
        method = getattr(self, scrubbed_method_name, None)

        self._method_cache[method_name] = method

        return method


class DefaultDataProcessor(DataProcessorBase):
    """
    This is the default data processor used by :class:`fitdecode.FitReader`. It
    derives from :class:`DataProcessorBase`.

    This data processor converts some raw values to more comfortable ones.

    .. seealso:: `StandardUnitsDataProcessor`, `DataProcessorBase`
    """

    def __init__(self):
        super().__init__()

    def process_type_bool(self, reader, field_data):
        """Just `bool` any ``bool`` typed FIT field unless value is `None`"""
        if field_data.value is not None:
            field_data.value = bool(field_data.value)

    def process_type_date_time(self, reader, field_data):
        """
        Convert ``date_time`` typed field values into `datetime.datetime` object
        if possible.

        That is, if value is not `None` and greater or equal than
        `FIT_DATETIME_MIN`.

        The resulting `datetime.datetime` object is timezone-aware (UTC).
        """
        if (field_data.value is not None and
                field_data.value >= FIT_DATETIME_MIN):
            field_data.value = datetime.datetime.fromtimestamp(
                FIT_UTC_REFERENCE + field_data.value,
                datetime.timezone.utc)
            field_data.units = None  # units were 's', set to None

    def process_type_local_date_time(self, reader, field_data):
        """
        Convert ``date_time`` typed field values into `datetime.datetime` object
        unless value is `None`.

        The resulting `datetime.datetime` object **IS NOT** timezone-aware, but
        this method assumes UTC at object construction to ensure consistency.
        """
        if field_data.value is not None:
            # This value was created on the device using its local timezone.
            # Unless we know that timezone, this value won't be correct.
            # However, if we assume UTC, at least it'll be consistent.
            field_data.value = datetime.datetime.utcfromtimestamp(
                FIT_UTC_REFERENCE + field_data.value)
            field_data.units = None

    def process_type_localtime_into_day(self, reader, field_data):
        """
        Convert ``localtime_into_day`` typed field values into `datetime.time`
        object unless value is `None`.
        """
        if field_data.value is not None:
            m, s = divmod(field_data.value, 60)
            h, m = divmod(m, 60)
            field_data.value = datetime.time(h, m, s)
            field_data.units = None

    def process_message_hr(self, reader, data_message):
        """
        Convert populated ``event_timestamp`` component values of the ``hr`` to
        `datetime.datetime` objects
        """
        if not data_message.has_field(profile.FIELD_NUM_HR_EVENT_TIMESTAMP_12):
            # We want to convert only populated *event_timestamp* fields that
            # were originally computed from the *event_timestamp_12* value
            return

        for field_data in data_message.get_fields(
                profile.FIELD_NUM_HR_EVENT_TIMESTAMP):
            if field_data is not None:
                field_data.value = datetime.datetime.fromtimestamp(
                    FIT_UTC_REFERENCE + field_data.value,
                    datetime.timezone.utc)
                field_data.units = None  # units were 's', set to None


class StandardUnitsDataProcessor(DefaultDataProcessor):
    """
    A `DefaultDataProcessor` that also:

    * Converts ``distance`` and ``total_distance`` fields to ``km``
      (standard's default is ``m``)
    * Converts all ``speed`` and ``*_speeds`` fields (by name) to ``km/h``
      (standard's default is ``m/s``)
    * Converts GPS coordinates (i.e. FIT's semicircles type) to ``deg``

    .. seealso:: `DefaultDataProcessor`, `DataProcessorBase`
    """

    def __init__(self):
        super().__init__()

    def on_process_field(self, reader, field_data):
        """
        Convert all ``*_speed`` fields using `process_field_speed`.

        All other units will use the default method.
        """
        if field_data.name and field_data.name.endswith('_speed'):
            self.process_field_speed(reader, field_data)
        else:
            super().on_process_field(reader, field_data)

    def process_field_distance(self, reader, field_data):
        if field_data.value is not None:
            field_data.value /= 1000.0
        field_data.units = 'km'

    def process_field_total_distance(self, reader, field_data):
        self.process_field_distance(reader, field_data)

    def process_field_speed(self, reader, field_data):
        if field_data.value is not None:
            factor = 60.0 * 60.0 / 1000.0

            # record.enhanced_speed field can be a tuple...
            # see https://github.com/dtcooper/python-fitparse/issues/62
            if isinstance(field_data.value, (tuple, list)):
                field_data.value = tuple(x * factor for x in field_data.value)
            else:
                field_data.value *= factor

        field_data.units = 'km/h'

    def process_units_semicircles(self, reader, field_data):
        if field_data.value is not None:
            field_data.value *= 180.0 / (2 ** 31)
        field_data.units = 'deg'
