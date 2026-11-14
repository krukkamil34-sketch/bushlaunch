; Instalator BushLaunch (Inno Setup 6).
; Budowanie: iscc installer\BushLaunch.iss /DAppVersion=0.3.0
; Wymaga wcześniej zbudowanego folderu dist\BushLaunch (jpackage --type app-image, z wbudowaną Javą).

#ifndef AppVersion
  #define AppVersion "0.3.0"
#endif
#define AppName "BushLaunch"
#define AppExe "BushLaunch.exe"
#define AppPublisher "BushStar"

[Setup]
; GUID identyfikuje aplikację przy aktualizacjach i odinstalowaniu - nie zmieniaj go.
AppId={{B82096A1-1A7C-466F-903F-DF3FA47D61DA}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
VersionInfoVersion={#AppVersion}
VersionInfoProductName={#AppName}
VersionInfoDescription={#AppName} - launcher Minecraft
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
; Domyślnie instalacja tylko dla bieżącego użytkownika (bez UAC); w oknie można wybrać "dla wszystkich".
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\dist
OutputBaseFilename=BushLaunch-Setup
SetupIconFile=bushlaunch.ico
UninstallDisplayIcon={app}\{#AppExe}
WizardStyle=modern
WizardImageFile=wizard.bmp,wizard@2x.bmp
WizardSmallImageFile=small.bmp,small@2x.bmp
Compression=lzma2/ultra64
SolidCompression=yes
CloseApplications=yes
RestartApplications=no
SetupLogging=yes

[Languages]
Name: "polish"; MessagesFile: "compiler:Languages\Polish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
polish.RemoveDataPrompt=Czy usunąć też dane launchera (pobrane wersje gry, biblioteki, assets, ustawienia)?%n%nFolder: %1%n%nWybierz "Nie", jeśli chcesz zachować swoje światy i pobrane gry.
english.RemoveDataPrompt=Also remove launcher data (downloaded game versions, libraries, assets, settings)?%n%nFolder: %1%n%nChoose "No" to keep your worlds and downloaded games.

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\dist\BushLaunch\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent

[Code]
// Przy odinstalowaniu pytamy, czy skasować też dane w %APPDATA%\.bushlaunch (domyślnie: zostaw).
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  DataDir: String;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    DataDir := ExpandConstant('{userappdata}\.bushlaunch');
    if DirExists(DataDir) then
    begin
      if not UninstallSilent then
        if MsgBox(FmtMessage(CustomMessage('RemoveDataPrompt'), [DataDir]), mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
          DelTree(DataDir, True, True, True);
    end;
  end;
end;
