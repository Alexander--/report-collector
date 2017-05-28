<?php
header("Content-Type: text/plain");

function bail($reason, $explanation) {
  header('HTTP/1.1 ' . $reason);
  die($explanation);
}

$hash_unsafe = $_SERVER['QUERY_STRING'];

if (strlen($hash_unsafe) > 20 || !is_numeric($hash_unsafe)) {
  bail("400 Bad Request", "Illegal argument");
}

header("Accept-Ranges: none");

$hash_int = intval($hash_unsafe);

include '../secrets.php';

$link = mysqli_connect($MYSQL_HOST, $MYSQL_U_RO, $MYSQL_PASS, $MYSQL_DB);

if (!$link) {
  bail('502 Bad Gateway', 'Backend connection failed');
}

mb_internal_encoding("UTF-8");

mysqli_query($link, "set names utf8mb4");

$query = "SELECT trace FROM x2440859_main.traces WHERE _id = ?";

if (!($stmt = mysqli_prepare($link, $query))) {
    bail('500 Server error', 'Prepare failed');
}

if (!mysqli_stmt_bind_param($stmt, "s", $hash_int)) {
    bail('500 Server error', 'Bind failed');
}

if (!($result = mysqli_stmt_execute($stmt)) || !mysqli_stmt_store_result($stmt)) {
    bail('502 Bad Gateway', 'Failed to retrieve file');
}

if (!mysqli_stmt_bind_result($stmt, $contents)) {
    $err = mysqli_stmt_error($stmt);

    bail("500 Server error", "Bind failed {$err}");
}

if (!mysqli_stmt_fetch($stmt)) {
    $err = mysqli_stmt_error($stmt);

    bail("404 Not Found", "File not found {$err}");
}

header("Accept-Ranges: none");
header('Content-Disposition: inline; filename="trace.txt"');

echo $contents;
?>
