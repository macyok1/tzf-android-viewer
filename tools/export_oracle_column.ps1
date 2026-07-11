<#
Экспорт одного столбца TZF через штатный TZF_API_x64.dll.
Это НЕ часть будущего ридера: скрипт создаёт эталон для побайтной проверки
самостоятельного декодера на ПК и затем на Android.

Пример:
  .\export_oracle_column.ps1 -InputFile C:\scan\Scan_001.tzf -Column 1000 -OutputFile C:\temp\column-1000.csv
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$InputFile,
    [Parameter(Mandatory)] [Int64]$Column,
    [Parameter(Mandatory)] [string]$OutputFile
)

$ErrorActionPreference = 'Stop'
$dll = 'C:\Program Files\Trimble\Trimble RealWorks 12.0\RealColor\TZF_API_x64.dll'
if (-not (Test-Path -LiteralPath $dll)) { throw "Не найдена $dll" }

if (-not ('TzfOracle' -as [type])) {
    $source = @"
using System;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;

public static class TzfOracle {
  const string Dll = @"$dll";
  [StructLayout(LayoutKind.Sequential)] struct Handle { public IntPtr value; }
  [StructLayout(LayoutKind.Sequential, Pack=4)] struct Point { public float x, y, z; }
  [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate void Callback(IntPtr p, long count, long column, IntPtr user);
  [DllImport(Dll, CharSet=CharSet.Unicode)] static extern Handle TZF_OpenFileW(string path, IntPtr callback);
  [DllImport(Dll)] static extern void TZF_CloseFile(Handle handle);
  [DllImport(Dll)] static extern long TZF_GetScanColumnCount(Handle handle);
  [DllImport(Dll)] static extern long TZF_GetXYZ(Handle handle, long column, Callback callback, IntPtr user);

  public static void ExportColumn(string input, long column, string output) {
    var handle = TZF_OpenFileW(input, IntPtr.Zero);
    if (handle.value == IntPtr.Zero) throw new InvalidDataException("TZF не открыт");
    try {
      if (column < 0 || column >= TZF_GetScanColumnCount(handle)) throw new ArgumentOutOfRangeException("column");
      using (var writer = new StreamWriter(output, false, new System.Text.UTF8Encoding(false))) {
        writer.WriteLine("row,x,y,z");
        Callback callback = (pointer, count, returnedColumn, user) => {
          for (long row = 0; row < count; row++) {
            var point = (Point)Marshal.PtrToStructure(IntPtr.Add(pointer, checked((int)(row * 12))), typeof(Point));
            writer.Write(row); writer.Write(',');
            writer.Write(point.x.ToString("G9", CultureInfo.InvariantCulture)); writer.Write(',');
            writer.Write(point.y.ToString("G9", CultureInfo.InvariantCulture)); writer.Write(',');
            writer.WriteLine(point.z.ToString("G9", CultureInfo.InvariantCulture));
          }
        };
        var status = TZF_GetXYZ(handle, column, callback, IntPtr.Zero);
        if (status != 0) throw new InvalidDataException("TZF_GetXYZ: " + status);
      }
    } finally { TZF_CloseFile(handle); }
  }
}
"@
    Add-Type -TypeDefinition $source -Language CSharp
}

[TzfOracle]::ExportColumn((Resolve-Path -LiteralPath $InputFile), $Column, $OutputFile)
