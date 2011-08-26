#!/usr/bin/perl

use strict;
use warnings;

# detecteer de titel van pagina's
# 1 regel heeft altijd nummer links en dan wat spam
# 104     Teletekst vr 26 aug

my $i = 0;
my $title = "";
while (<>) {
    if ($title eq "") {
        if ($i == 1) {
            # of een rij sterretjes '*'
            # of de titel (bv 102/103)
            if (/\*+$/) {
                # sterren, niet interessant
                goto verder;
            }
            # anders de titel
            $title = $_;
            # tenzij het iets is als 1/3
            if ($title =~ m|\d+/\d+|) {
                $title = ""
            }
        }
        if ($i == 2) {
            # niks gevonden, nu misschien:
            # ************  J  O  U  R  N  A  A  L 
            # minder dan 40 sterren, dan titel
            if (substr $_, 39, 1 ne "*") {
                $title = $_;
            }
        }
    }
    

verder:
    print;
    $i++;
}

chomp $title;
$title =~ s/\*//g;
$title =~ s/.*?([^ ])/$1/;
print "\n$title\n";
