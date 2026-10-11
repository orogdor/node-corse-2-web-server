; HomeJoolink native Windows app installer. Bundles the self-contained .NET and VLC files
; into a single INSTALLER exe. After installation, app launches without extracting
; a ~216 MB runtime bundle every time.
#define AppName "HomeJoolink"
#define AppVersion "1.0.5"
#define ExeName "HomeJoolink.exe"

[Setup]
AppId=HomeJoolink.Windows.Test
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName=HomeJoolink 1.0.5 (Windows TEST)
AppPublisher=HomeJoolink (community test)
DefaultDirName={localappdata}\Programs\HomeJoolink
DefaultGroupName=HomeJoolink
UninstallDisplayIcon={app}\{#ExeName}
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
OutputDir=dist
OutputBaseFilename=HomeJoolink-Windows-v1.0.5-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=yes
SetupLogging=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"; Flags: unchecked

[Files]
Source: "..\publish-installed\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\HomeJoolink"; Filename: "{app}\{#ExeName}"
Name: "{autodesktop}\HomeJoolink"; Filename: "{app}\{#ExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#ExeName}"; Description: "Start HomeJoolink"; Flags: nowait postinstall skipifsilent
