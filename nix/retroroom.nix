{
  lib,
  stdenv,
  cmake,
  ninja,
  pkg-config,
  qt6,
}:

stdenv.mkDerivation {
  pname = "retroroom";
  version = "1.0.0";

  src = ../plugins/retroroom;

  nativeBuildInputs = [
    cmake
    ninja
    pkg-config
  ];

  buildInputs = [
    qt6.qtbase
    qt6.qtdeclarative
    qt6.qtquick3d
  ];

  cmakeFlags = [
    "-DCMAKE_BUILD_TYPE=Release"
  ];

  installPhase = ''
    runHook preInstall

    mkdir -p $out/lib/qt-6/qml/RetroRoom
    cp -r RetroRoom/* $out/lib/qt-6/qml/RetroRoom/

    runHook postInstall
  '';

  meta = {
    description = "RetroRoom Qt Quick 3D background room plugin";
    platforms = [ "x86_64-linux" ];
    license = lib.licenses.gpl3Plus;
  };
}
