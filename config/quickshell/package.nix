{
  lib,
  symlinkJoin,
  makeWrapper,
  quickshell,
  kdePackages,
  configPath ? ./.,
}: let
  qmlPath = lib.makeSearchPath "lib/qt-6/qml" [
    kdePackages.qtbase
    kdePackages.qtdeclarative
    kdePackages.qt5compat
  ];
in
  symlinkJoin {
    pname = "imp98";
    inherit (quickshell) version;

    paths = [quickshell];
    nativeBuildInputs = [makeWrapper];

    postBuild = ''
      makeWrapper $out/bin/quickshell $out/bin/imp98 \
        --set QML2_IMPORT_PATH "${qmlPath}" \
        --add-flags '-p ${configPath}'
    '';

    meta.mainProgram = "imp98";
  }
