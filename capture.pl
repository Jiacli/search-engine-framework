#!/usr/bin/perl

#
#  This perl script illustrates fetching information from a CGI program
#  that typically gets its data via an HTML form using a POST method.
#
#  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
#

use LWP::Simple;

my $fileIn = 'trec_eval'; #Indri-Bow.teIn#    #Indri-Sdm.teIn#
my $url = 'http://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/tes.cgi';

#  Fill in your USERNAME and PASSWORD below.

my $ua = LWP::UserAgent->new();
   $ua->credentials("boston.lti.cs.cmu.edu:80", "HTS", "jiachenl", "NmViMjM4");
my $result = $ua->post($url,
		       Content_Type => 'form-data',
		       Content      => [ logtype => 'Detailed',	# cgi parameter
					 infile => [$fileIn],	# cgi parameter
					 hwid => 'HW4'		# cgi parameter
		       ]);

my $result = $result->as_string;	# Reformat the result as a string
   $result =~ s/<br>/\n/g;		# Replace <br> with \n for clarity

my @array = split("\n",$result);
my @baseline = ("0.0170","0.2721","0.0340","0.0165","0.3836","0.0615","0.0344","0.0007","0.1168","0.0432");
my $win = 0;
my $loss = 0;
my $i = 0;
foreach $line(@array){
	@item = split(/\s+/,$line);
	#print @item[0],"\n";
	if(@item[0] eq "map")
	{
		print "map".$i."=".@item[2],"\n";
		
		if(@item[2] gt @baseline[$i] and $i < 10)
		{
			$win ++;
		}
		if(@item[2] lt @baseline[$i] and $i < 10)
		{
			$loss ++;
		}
		$i ++;
	}
	if(@item[1] eq "all")
	{
		if(@item[0] eq "P10")
		{
			print "P10=".@item[2],"\n"
		}
		if(@item[0] eq "P20")
		{
			print "P20=".@item[2],"\n"
		}
		if(@item[0] eq "P30")
		{
			print "P30=".@item[2],"\n"
		}
	}
}
print "win/loss=".$win."/".$loss,"\n";
exit;