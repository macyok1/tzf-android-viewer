<# Экспорт эталонного сферического столбца через установленный RealWorks. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$InputFile,
    [Parameter(Mandatory)] [Int64]$Column,
    [Parameter(Mandatory)] [string]$OutputFile
)

$ErrorActionPreference = 'Stop'
$dll = 'C:\Program Files\Trimble\Trimble RealWorks 12.0\RealColor\TZF_API_x64.dll'
if (-not (Test-Path -LiteralPath $dll)) { throw "Не найден $dll" }

if (-not ('TzfSphericalOracle' -as [type])) {
    $source = @"
using System;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;

public static class TzfSphericalOracle {
  const string Dll = @"$dll";
  [StructLayout(LayoutKind.Sequential)] struct Handle { public IntPtr value; }
  [StructLayout(LayoutKind.Sequential, Pack=4)]
  struct Point { public float rho, azimuth, polar; }
  [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
  delegate void Callback(IntPtr p, long count, long column, IntPtr user);
  [DllImport(Dll, CharSet=CharSet.Unicode)]
  static extern Handle TZF_OpenFileW(string path, IntPtr callback);
  [DllImport(Dll)] static extern void TZF_CloseFile(Handle handle);
  [DllImport(Dll)] static extern long TZF_GetScanColumnCount(Handle handle);
  [DllImport(Dll)]
  static extern long TZF_GetSpherical(Handle handle, long column,
                                      Callback callback, IntPtr user);

  public static void ExportColumn(string input, long column, string output) {
    var handle = TZF_OpenFileW(input, IntPtr.Zero);
    if (handle.value == IntPtr.Zero) throw new InvalidDataException("TZF не открыт");
    try {
      if (column < 0 || column >= TZF_GetScanColumnCount(handle))
        throw new ArgumentOutOfRangeException("column");
      using (var writer = new StreamWriter(output, false,
          new System.Text.UTF8Encoding(false))) {
        writer.WriteLine("row,rho,azimuth,polar");
        Callback callback = (pointer, count, returnedColumn, user) => {
          for (long row = 0; row < count; ++row) {
            var point = (Point)Marshal.PtrToStructure(
                IntPtr.Add(pointer, checked((int)(row * 12))), typeof(Point));
            writer.Write(row); writer.Write(',');
            writer.Write(point.rho.ToString("G9", CultureInfo.InvariantCulture));
            writer.Write(',');
            writer.Write(point.azimuth.ToString("G9", CultureInfo.InvariantCulture));
            writer.Write(',');
            writer.WriteLine(point.polar.ToString("G9", CultureInfo.InvariantCulture));
          }
        };
        var status = TZF_GetSpherical(handle, column, callback, IntPtr.Zero);
        if (status != 0) throw new InvalidDataException("TZF_GetSpherical: " + status);
      }
    } finally { TZF_CloseFile(handle); }
  }
}
"@
    Add-Type -TypeDefinition $source -Language CSharp
}

[TzfSphericalOracle]::ExportColumn(
    (Resolve-Path -LiteralPath $InputFile), $Column, $OutputFile)
