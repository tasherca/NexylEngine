{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  buildInputs = with pkgs; [
    openjdk
    gradle
    mesa
    libGL
    xorg.libX11
    xorg.libXrandr
    xorg.libXcursor
    xorg.libXi
  ];

  shellHook = ''
    export JAVA_HOME=${pkgs.openjdk}
    export LD_LIBRARY_PATH=${pkgs.mesa}/lib:${pkgs.libGL}/lib:$LD_LIBRARY_PATH
  '';
}

