/*
 * Copyright (C) 2015 Microchip Technology Inc. and its subsidiaries.  You may use this software and any derivatives
 * exclusively with Microchip products.
 *
 * THIS SOFTWARE IS SUPPLIED BY MICROCHIP "AS IS".  NO WARRANTIES, WHETHER EXPRESS, IMPLIED OR STATUTORY, APPLY TO THIS
 * SOFTWARE, INCLUDING ANY IMPLIED WARRANTIES OF NON-INFRINGEMENT, MERCHANTABILITY, AND FITNESS FOR A PARTICULAR
 * PURPOSE, OR ITS INTERACTION WITH MICROCHIP PRODUCTS, COMBINATION WITH ANY OTHER PRODUCTS, OR USE IN ANY APPLICATION.
 *
 * IN NO EVENT WILL MICROCHIP BE LIABLE FOR ANY INDIRECT, SPECIAL, PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSS, DAMAGE,
 * COST OR EXPENSE OF ANY KIND WHATSOEVER RELATED TO THE SOFTWARE, HOWEVER CAUSED, EVEN IF MICROCHIP HAS BEEN ADVISED OF
 * THE POSSIBILITY OR THE DAMAGES ARE FORESEEABLE.  TO THE FULLEST EXTENT ALLOWED BY LAW, MICROCHIP'S TOTAL LIABILITY ON
 * ALL CLAIMS IN ANY WAY RELATED TO THIS SOFTWARE WILL NOT EXCEED THE AMOUNT OF FEES, IF ANY, THAT YOU HAVE PAID
 * DIRECTLY TO MICROCHIP FOR THIS SOFTWARE.
 *
 * MICROCHIP PROVIDES THIS SOFTWARE CONDITIONALLY UPON YOUR ACCEPTANCE OF THESE TERMS.
 */

package com.microchip.mldpterminal3;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

/**
 * Class to create the various alerts used by the MldpTerminalActivity
 * The strings for these alert dialogs are defined in the dialog_strings.xml file
 * Only a single AlertDialog dialog is reused for each alert.
 *
 */
public class ShowAlertDialogs {

    private AlertDialog dialog;											                            //Object variable for an AlertDialog dialog box
    private AlertDialog.Builder builder; 								                            //Object variable for an AlertDialog.Builder to be able to create dialog boxes

    // ----------------------------------------------------------------------------------------------------------------
    // Constructor creates the AlertDialog.Builder to build each dialog.
    public ShowAlertDialogs(Context context) {
        builder = new AlertDialog.Builder(context); 						                        //Instantiate an AlertDialog.Builder to be able to create dialog boxes
        dialog = builder.create();                                                                  //Instantiate an AlertDialog so that we do not need to check if it is null
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Dismiss any dialog that is showing. Has no effect if the dialog is not showing.
    public void dismiss() {
        dialog.dismiss();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Show the Help dialog when the Help menu is selected. OK button dismisses the dialog.
    public void showHelpMenuDialog(Context context) {
//        final SpannableString string = new SpannableString(Html.fromHtml(context.getString(R.string.help_contents)));
        dialog.dismiss();
        builder.setTitle(R.string.help_title);                                                      //Set up the AlertDialog that will contain the Help text
        builder.setMessage(Html.fromHtml(context.getString(R.string.help_contents)));
//        builder.setMessage(string);
        builder.setPositiveButton(R.string.help_ok_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {                                   // User clicked OK button
            }
        });
        builder.setNegativeButton(null, null);
        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        ((TextView)dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Show the About dialog when the About menu is selected. OK button dismisses the dialog.
    public void showAboutMenuDialog() {
        dialog.dismiss();
        builder.setTitle(R.string.about_title);                                                     //Set up the AlertDialog that will contain the About text
        builder.setMessage(R.string.about_contents);
        builder.setPositiveButton(R.string.about_ok_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {                                   // User clicked OK button
            }
        });
        builder.setNegativeButton(null, null);
        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();                                                                              //Show the AlertDialog that has the About text
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Show the Exit dialog when the Exit menu is selected. Cancel button dismisses the dialog. OK button executes a Runnable.
    public void showExitMenuDialog(final Runnable callback) {
        dialog.dismiss();
        builder.setTitle(R.string.exit_title);                                                      //Set up the AlertDialog that will contain the Exit warning
        builder.setMessage(R.string.exit_contents);
        builder.setPositiveButton(R.string.exit_ok_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {                                   //User clicked OK button
                callback.run();
            }
        });
        builder.setNegativeButton(R.string.exit_cancel_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {                                   //User clicked Cancel button
            }                                                                                       //Don't do anything, continue as before
        });
        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Show a dialog to inform the user that we are attempting to connect to the device that was previously used and saved.
    public void showAutoConnectDialog(final Runnable callback) {
        dialog.dismiss();
        builder.setTitle(R.string.autoconnect_title);                                                   //Set up the AlertDialog that will let the user know that auto connect is in progress
        builder.setMessage(R.string.autoconnect_contents);
        builder.setPositiveButton(null, null);
        builder.setNegativeButton(R.string.autoconnect_cancel_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {                                       //User clicked Cancel button so start a scan
                callback.run();
            }                                                                                       //Don't do anything, continue as before
        });
        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Show a dialog that the connection attempt failed.
    public void showFailedToConnectDialog(final Runnable callback) {
        dialog.dismiss();
        builder.setTitle(R.string.noconnect_title);                                                 //Set up the AlertDialog that will let the user know that the connection failed
        builder.setMessage(R.string.noconnect_contents);
        builder.setPositiveButton(R.string.noconnect_ok_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {                                   //User clicked OK button
                callback.run();
            }
        });
        builder.setNegativeButton(R.string.noconnect_cancel_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {                                   //User clicked Cancel button
            }                                                                                       //Don't do anything, continue as before
        });
        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Show a dialog that the existing connection was lost. Probably caused by the remote Bluetooth device disconnecting, powering off, or going out of range.
    public void showLostConnectionDialog(final Runnable callback) {
        dialog.dismiss();
        builder.setTitle(R.string.lost_title);                                                      //Set up the AlertDialog that will let the user know that the connection failed
        builder.setMessage(R.string.lost_contents);
        builder.setPositiveButton(R.string.lost_ok_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {                                   //User clicked OK button
                callback.run();
            }
        });
        builder.setNegativeButton(R.string.lost_cancel_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {                                   //User clicked Cancel button
            }                                                                                       //Don't do anything, continue as before
        });
        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

}
