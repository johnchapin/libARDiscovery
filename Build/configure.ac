#                                               -*- Autoconf -*-
# Process this file with autoconf to produce a configure script.
AC_INIT([libARDiscovery], [3.1.0], [ardrone@ardrone.org])
AC_CONFIG_HEADERS([config.h])
AC_CONFIG_AUX_DIR(config)
AC_CONFIG_MACRO_DIR(m4)

m4_include([../../ARSDKBuildUtils/Utils/m4/ARSDK.m4])

# automake initialisation and check for minimal automake API version 1.9
AM_INIT_AUTOMAKE

# use the C compiler for the following checks
AC_LANG([C])

##########################################################################
# Debug compilation support
##########################################################################
AC_MSG_CHECKING([whether to build with debug information])
AC_ARG_ENABLE([debug],
    [AS_HELP_STRING([--enable-debug],
    [enable debug data generation (def=no)])],
    [debugit="$enableval"],
    [debugit=no])
AC_MSG_RESULT([$debugit])

if test x"$debugit" = x"yes"; then
    AC_DEFINE([DEBUG],[],[Debug Mode])
    LOCAL_CFLAGS+=" -g -Wall -Werror -Wno-uninitialized -O0"
else
    AC_DEFINE([NDEBUG],[],[No-debug Mode])
    LOCAL_CFLAGS+=" -O3"
fi
AM_CONDITIONAL([DEBUG_MODE], [test "$debugit" = "yes"])

AC_MSG_CHECKING([whether to build for avahi daemon])
AC_ARG_ENABLE([avahi-nodbus],
    [AS_HELP_STRING([--enable-avahi-nodbus],
    [enable avahi wihtout dbus support (def=no)])],
    [nodbus="$enableval"],
    [nodbus=no])
AC_MSG_RESULT([$nodbus])

AM_CONDITIONAL([AVAHI_NODBUS], [test "$nodbus" = "yes"])

##########################################################################
# Non versionned .so compilation support (for Android)
##########################################################################
AC_MSG_CHECKING([whether to put version number in .so files])
AC_ARG_ENABLE([so-version],
    [AS_HELP_STRING([--disable-so-version],
    [disable version names in .so files [default=enabled]])],
    [sover="$enableval"],
    [sover=yes])
AC_MSG_RESULT([$sover])

##########################################################################
# Define includes directories
##########################################################################
# include local headers first
LOCAL_CFLAGS+=" -I$srcdir/../Includes -I$srcdir/../Sources"


CPPFLAGS+="$LOCAL_CFLAGS"
CFLAGS+="$LOCAL_CFLAGS"
OBJCFLAGS+="$LOCAL_CFLAGS"

# add libARxxx deps

AR_DEPENDS([libARSAL], [Y], [libARSAL/ARSAL.h])


##########################################################################
# Checks for programs.
AC_PROG_CC
AC_PROG_LIBTOOL
AC_PROG_OBJC

AC_CHECK_PROGS([DOXYGEN], [doxygen])
m4_include(doxygen.ac)
DX_HTML_FEATURE(ON)
DX_CHM_FEATURE(OFF)
DX_CHI_FEATURE(OFF)
DX_MAN_FEATURE(OFF)
DX_RTF_FEATURE(OFF)
DX_XML_FEATURE(OFF)
DX_PDF_FEATURE(OFF)
DX_PS_FEATURE(OFF)
DX_INIT_DOXYGEN([libARDiscovery], [doxygen.cfg], [$srcdir/../Doc])
AM_CONDITIONAL([HAVE_DOXYGEN], [test -n "$DOXYGEN"])

# Check for python and run prebuild PY script
AM_PATH_PYTHON(,, AC_MSG_ERROR(Python is required to generate source files for this library))

# Run prebuild PY script
AC_MSG_NOTICE([Executing python prebuild commands])
AM_CONDITIONAL([PYTHON_RUN_OK], [$PYTHON $srcdir/../../ARSDKBuildUtils/Utils/Python/ARSDK_PrebuildActions.py $srcdir])
AM_COND_IF([PYTHON_RUN_OK], , AC_MSG_ERROR(Python processing failed))

# Checks for header files.
AC_HEADER_STDC

##########################################################################
# Check headers
##########################################################################

# Check for Bonjour
AC_LANG_PUSH([Objective C])
if test x$ac_cv_objc_compiler_gnu = xyes; then
    AC_CHECK_HEADERS([CoreBluetooth/CoreBluetooth.h arpa/inet.h netdb.h Foundation/Foundation.h],
        [LDFLAGS="$LDFLAGS -framework CoreBluetooth"],
        AC_MSG_WARN(Missing headers for Bonjour support))
else
    AC_MSG_WARN(No Objective-C support on the given platform)
fi

AM_CONDITIONAL([HAVE_BONJOUR], [test x"$ac_cv_header_CoreBluetooth_CoreBluetooth_h" = x"yes" && \
                                test x"$ac_cv_header_arpa_inet_h" = x"yes" && \
                                test x"$ac_cv_header_netdb_h" = x"yes" && \
                                test x"$ac_cv_header_Foundation_Foundation_h" = x"yes"])
AC_LANG_POP([Objective C])

# Checks for Avahi
AC_CHECK_HEADERS([avahi-client/client.h avahi-client/publish.h avahi-common/error.h],,
    AC_MSG_WARN(Missing headers for Avahi support))
AM_CONDITIONAL([HAVE_AVAHI], [test x"$ac_cv_header_avahi_client_client_h" = x"yes" && \
                              test x"$ac_cv_header_avahi_client_publish_h" = x"yes" && \
                              test x"$ac_cv_header_avahi_common_error_h" = x"yes"])
if test x"$ac_cv_header_avahi_client_client_h" = x"yes" && \
   test x"$ac_cv_header_avahi_client_publish_h" = x"yes" && \
   test x"$ac_cv_header_avahi_common_error_h" = x"yes"; then
   LDFLAGS+=" -lavahi-client -lavahi-common"
fi

# Checks for typedefs, structures, and compiler characteristics.
AC_TYPE_SIZE_T

# Generate libtool version info number
if test x"$sover" = x"yes"; then
    LDFLAGS+=" -release $PACKAGE_VERSION"
else
    LDFLAGS+=" -avoid-version"
fi

# distribute additional compiler and linker flags
# --> set these variables instead of CFLAGS or LDFLAGS
AC_SUBST([CFLAGS])
AC_SUBST([LDFLAGS])
AC_SUBST([OBJCFLAGS])

# Checks for library functions.


# Generates Makefile
AC_CONFIG_FILES([Makefile])
AC_OUTPUT

echo "
  $PACKAGE_NAME version $PACKAGE_VERSION
  Prefix.........: $prefix
  Debug Build....: $debugit
  Objc Compiler..: $ac_cv_objc_compiler_gnu
  C Compiler.....: $CC $CFLAGS
  Linker.........: $LD $LDFLAGS $LIBS
  Doxygen........: ${DOXYGEN:-NONE}
"
